// Author: A0144939R

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;

class Alice {  // Alice is a TCP client
  
  private ObjectOutputStream toBob;   // to send session key to Bob
  private ObjectInputStream fromBob;  // to read encrypted messages from Bob
  private Crypto crypto;        // object for encryption and decryption
  public static final String MESSAGE_FILE = "msgs.txt"; // file to store messages
  
  public static void main(String[] args) {
    
    // Check if the number of command line argument is 2
    if (args.length != 2) {
      System.err.println("Usage: java Alice BobIP BobPort");
      System.exit(1);
    }
    
    new Alice(args[0], args[1]);
  }
  
  // Constructor
  public Alice(String ipStr, String portStr) {
    
    this.crypto = new Crypto();
    
    Socket connectionSkt = null;
    try {
        connectionSkt = new Socket(InetAddress.getByName(ipStr), Integer.parseInt(portStr));
    } catch (NumberFormatException e) {
        System.out.println("Port must be a number");
        System.exit(1);
    } catch (UnknownHostException e) {
        System.out.println("Unknown Host provided");
        System.exit(1);
    } catch (IOException e) {
        System.out.println("Error creating connection socket");
        System.exit(1);
    }
    
    try {
        this.toBob = new ObjectOutputStream(connectionSkt.getOutputStream());
        this.fromBob = new ObjectInputStream(connectionSkt.getInputStream());
      } catch (IOException ioe) {
        System.out.println("Error: cannot get input/output streams");
        System.exit(1);
      }
    
    // Send session key to Bob
    sendSessionKey();
    
    // Receive encrypted messages from Bob,
    // decrypt and save them to file
    receiveMessages();
  }
  
  // Send session key to Bob
  public void sendSessionKey() {
      SealedObject encryptedSecretKey = crypto.getSessionKey();
      try {
        this.toBob.writeObject(encryptedSecretKey);
    } catch (IOException e) {
        System.out.println("Error sending messages to Bob");
        System.exit(1);
    }
        
  }
  
  // Receive messages one by one from Bob, decrypt and write to file
  public void receiveMessages() {
      
    PrintWriter printWriter = null;
    try {
        printWriter = new PrintWriter(new File(MESSAGE_FILE));
    } catch (FileNotFoundException e) {
        System.out.println("Error: file not found");
        System.exit(1);
    }
    SealedObject message;
    try {
        message = (SealedObject)this.fromBob.readObject();
        while (true) {
            String decryptedMessage = crypto.decryptMsg(message);
            printWriter.write(decryptedMessage);
            printWriter.write("\r\n");
            message = (SealedObject)this.fromBob.readObject();
        }
    } catch (ClassNotFoundException e) {
        System.out.println("Error: cannot typecast to SealedObject");
        System.exit(1); 
    } catch (IOException e) {
        printWriter.close(); 
    }
  
  }
  
  /*****************/
  /** inner class **/
  /*****************/
  class Crypto {
    
    // Bob's public key, to be read from file
    private PublicKey pubKey;
    // Alice generates a new session key for each communication session
    private SecretKey sessionKey;
    // File that contains Bob' public key
    public static final String PUBLIC_KEY_FILE = "public.key";
    
    // Constructor
    public Crypto() {
      // Read Bob's public key from file
      readPublicKey();
      // Generate session key dynamically
      initSessionKey();
    }
    
    // Read Bob's public key from file
    public void readPublicKey() {
      // key is stored as an object and need to be read using ObjectInputStream.
      // See how Bob read his private key as an example.
        
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
    
    // Generate a session key
    public void initSessionKey() {
      // suggested AES key length is 128 bits
        KeyGenerator keyGenerator = null;
        try {
            keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(128);
            sessionKey = keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Error: No such algorithm found");
            System.exit(1); 
        }

        
        
    }
    
    // Seal session key with RSA public key in a SealedObject and return
    public SealedObject getSessionKey() {
    
        SealedObject sessionKeyObj = null;
      // Alice must use the same RSA key/transformation as Bob specified
        
      // RSA imposes size restriction on the object being encrypted (117 bytes).
      // Instead of sealing a Key object which is way over the size restriction,
      // we shall encrypt AES key in its byte format (using getEncoded() method).
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);
            sessionKeyObj = new SealedObject(sessionKey.getEncoded(), cipher);
        } catch (GeneralSecurityException gse) {
            System.out.println("Error: wrong cipher to encrypt message");
            System.exit(1);
        } catch (IOException ioe) {
            System.out.println("Error creating SealedObject");
            System.exit(1);
        }
      
        return sessionKeyObj;
      
    }
    
    // Decrypt and extract a message from SealedObject
    public String decryptMsg(SealedObject encryptedMsgObj) {
      
      String plainText = null;
      
      // Alice and Bob use the same AES key/transformation
      try {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, sessionKey);
        plainText = (String) encryptedMsgObj.getObject(cipher);
      } catch (GeneralSecurityException gse) {
        System.out.println("Error: wrong cipher to decrypt message");
        System.exit(1);
      } catch (IOException ioe) {
        System.out.println("Error decrypting with session key");
        System.exit(1);
      } catch (ClassNotFoundException e) {
        System.out.println("Error: cannot typecast to String");
        System.exit(1); 
      }
      
      
      return plainText;
    }
  }
}