// Written by: Lifeng

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.Scanner;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SealedObject;
import javax.crypto.spec.SecretKeySpec;

// Bob receives AES session key from Alice
// Bob reads messages line by line from file
// Bob sends encrypted messages to Alice

class Bob {  // Bob is a TCP server
  
  private ObjectInputStream fromAlice;  // channel to read session key from Alice
  private ObjectOutputStream toAlice;   // channel to send encrypted messages to Alice
  private Crypto crypto;    // for message encryption and decryption
  public static final String MESSAGE_FILE = "docs.txt"; // file to send
  
  public static void main(String[] args)  {
    
    // Check if the number of command line argument is 1
    if (args.length != 1) {
      System.err.println("Usage: java Bob BobPort");
      System.exit(1);
    }
    
    new Bob(args[0]);
  }
  
  // Constructor
  public Bob(String portStr) {
    
    this.crypto = new Crypto();
    
    int port = Integer.parseInt(portStr);
    
    ServerSocket welcomeSkt = null;  // wait for Alice to connect
    Socket connectionSkt = null;     // socket used to talk to Alice
    
    try {
      welcomeSkt = new ServerSocket(port);
    } catch (IOException ioe) {
      System.out.println("Error creating welcome socket");
      System.exit(1);
    }
    
    System.out.println("Bob listens at port " + port);
    
    // Create a separate socket to connect to a client
    try {
      connectionSkt = welcomeSkt.accept();
    } catch (IOException ioe) {
      System.out.println("Error creating connection socket");
      System.exit(1);
    }
    
    try {
      this.toAlice = new ObjectOutputStream(connectionSkt.getOutputStream());
      this.fromAlice = new ObjectInputStream(connectionSkt.getInputStream());
    } catch (IOException ioe) {
      System.out.println("Error: cannot get input/output streams");
      System.exit(1);
    }
    
    // Receive session key from Alice
    getSessionKey();
    
    // Send encrypted messages to Alice
    sendMessages();
    
    // Clean up
    try {
      welcomeSkt.close();
      connectionSkt.close();
    } catch (IOException ioe) {
      System.out.println("Error closing TCP sockets");
      System.exit(1);
    }
  }
  
  // Receive session key from Alice
  public void getSessionKey() {
    
    try {
      SealedObject sessionKeyObj = (SealedObject)this.fromAlice.readObject();
      this.crypto.setSessionKey(sessionKeyObj);
    } catch (IOException ioe) {
      System.out.println("Error receiving session key from Alice");
      System.exit(1);
    } catch (ClassNotFoundException ioe) {
      System.out.println("Error: cannot typecast to class SealedObject");
      System.exit(1); 
    }
  }
  
  // Read messages one by one from file, encrypt and send them to Alice
  public void sendMessages() {
    
    try {
      
      Scanner fromFile = new Scanner(new File(MESSAGE_FILE));
      
      // Read till the end of the file
      while ( fromFile.hasNext() ) {
        String message = fromFile.nextLine();
        SealedObject encryptedMsg = this.crypto.encryptMsg(message);
        this.toAlice.writeObject(encryptedMsg);
      }
      
      fromFile.close();  // close input file stream
      System.out.println("All messages are sent to Alice");
      
    } catch (FileNotFoundException fnfe) {
      System.out.println("Error: " + MESSAGE_FILE + " doesn't exist");
      System.exit(1);
    } catch (IOException ioe) {
      System.out.println("Error sending messages to Alice");
      System.exit(1);
    }
  }
  
  /*****************/
  /** inner class **/
  /*****************/
  class Crypto {
    
    // Use the same RSA public and private keys for all sessions.
    private PublicKey pubKey;    // Bob's public key is known to Alice
    private PrivateKey privKey;  // private key is a secret
    // Session key is received from Alice
    private SecretKey sessionKey;
    // File that contains public key
    public static final String PUBLIC_KEY_FILE = "public.key";
    // File that contains private key
    public static final String PRIVATE_KEY_FILE = "private.key";
    
    // Constructor
    public Crypto() {
      initRSAKeys();
    }
    
    // Read a pair of public and private keys from files,
    // or if files don't exit, create a new pair.
    public void initRSAKeys() {
      
      // Read keys from files
      File pubKeyFile = new File(PUBLIC_KEY_FILE);
      File privKeyFile = new File(PRIVATE_KEY_FILE);
      if ( pubKeyFile.exists() && !pubKeyFile.isDirectory() &&
          privKeyFile.exists() && !privKeyFile.isDirectory() ) {
        readPublicKey();
        readPrivateKey();
        return;
      }
      
      // Keys don't exit, generate new ones
      System.out.println("Generate new keys");
      
      KeyPairGenerator keyGen = null;
      try {
        keyGen = KeyPairGenerator.getInstance("RSA");
      } catch (NoSuchAlgorithmException nsae) {
        System.out.println("Error: cannot generate RSA keys");
        System.exit(1);
      }
      
      keyGen.initialize(1024); // key length is 1024 bits
      KeyPair keys = keyGen.generateKeyPair();
      this.pubKey = keys.getPublic();
      this.privKey = keys.getPrivate();
      
      // Save keys in files for future use
      savePublicKey();
      savePrivateKey();
    }
    
    // Receive a session key from Alice
    public void setSessionKey(SealedObject sessionKeyObj) {
      
      try {
        // getInstance(crypto algorithm/feedback mode/padding scheme)
        // Alice will use the same key/transformation
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, this.privKey);
        
        // Receive an AES key in "encoded form" from Alice
        byte[] rawKey = (byte[])sessionKeyObj.getObject(cipher);
        // Reconstruct AES key from encoded form
        this.sessionKey = new SecretKeySpec(rawKey, 0, rawKey.length, "AES");
      } catch (GeneralSecurityException gse) {
        System.out.println("Error: wrong cipher to decrypt session key");
        System.exit(1);
      } catch (IOException ioe) {
        System.out.println("Error receiving session key");
        System.exit(1);
      } catch (ClassNotFoundException ioe) {
        System.out.println("Error: cannot typecast to byte array");
        System.exit(1); 
      }
    }
    
    // Encrypt a message and encapsulate it as a SealedObject
    public SealedObject encryptMsg(String msg) {
      
      SealedObject sessionKeyObj = null;
      
      try {
        // getInstance(crypto algorithm/feedback mode/padding scheme)
        // Alice will use the same key/transformation
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey);
        sessionKeyObj = new SealedObject(msg, cipher);
      } catch (GeneralSecurityException gse) {
        System.out.println("Error: wrong cipher to encrypt message");
        System.exit(1);
      } catch (IOException ioe) {
        System.out.println("Error creating SealedObject");
        System.exit(1);
      }
      
      return sessionKeyObj;
    }
    
    // Save public key to file
    public void savePublicKey() {
      
      try {
        ObjectOutputStream oos = 
          new ObjectOutputStream(new FileOutputStream(PUBLIC_KEY_FILE));
        oos.writeObject(this.pubKey);
        oos.close();
      } catch (IOException oie) {
        System.out.println("Error saving public key to file");
        System.exit(1);
      }
      
      System.out.println("Public key saved to file " + PUBLIC_KEY_FILE);
    }
    
    // Save private key to file
    public void savePrivateKey() {
      
      try {
        ObjectOutputStream oos = 
          new ObjectOutputStream(new FileOutputStream(PRIVATE_KEY_FILE));
        oos.writeObject(this.privKey);
        oos.close();
      } catch (IOException oie) {
        System.out.println("Error saving private key to file");
        System.exit(1);
      }
      
      System.out.println("Private key saved to file " + PRIVATE_KEY_FILE);
    }
    
    // Read public key from a file
    public void readPublicKey() {
      
      try {
        ObjectInputStream ois = 
          new ObjectInputStream(new FileInputStream(PUBLIC_KEY_FILE));
        this.pubKey = (PublicKey)ois.readObject();
        ois.close();
      } catch (IOException oie) {
        System.out.println("Error reading public key from file");
        System.exit(1);
      } catch (ClassNotFoundException cnfe) {
        System.out.println("Error: cannot typecast to class PublicKey");
        System.exit(1);            
      }
      
      System.out.println("Public key read from file " + PUBLIC_KEY_FILE);
    }
    
    // Read private key from a file
    public void readPrivateKey() {
      
      try {
        ObjectInputStream ois = 
          new ObjectInputStream(new FileInputStream(PRIVATE_KEY_FILE));
        this.privKey = (PrivateKey)ois.readObject();
        ois.close();
      } catch (IOException oie) {
        System.out.println("Error reading private key from file");
        System.exit(1);
      } catch (ClassNotFoundException cnfe) {
        System.out.println("Error: cannot typecast to class PrivateKey");
        System.exit(1);
      }
      
      System.out.println("Private key read from file " + PRIVATE_KEY_FILE);
    }
  }
}