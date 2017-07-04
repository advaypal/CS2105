import java.net.*;
import java.nio.*;
import java.util.Scanner;
import java.util.zip.CRC32;


class Sender {
  private int currentState = 0;  
  public static void main(String[] args) throws Exception {
    // check if the number of command line argument is 4
    if (args.length != 1) {
      System.out.println("Usage: java Sender <unreliNetPort>");
      System.exit(1);
    }
    new Sender("localhost",  Integer.parseInt(args[0]));
  }
  
  public Sender(String host, int port) throws Exception {
    // Do not change this
    Scanner sc = new Scanner(System.in);
    while(sc.hasNextLine()) {
      String line = sc.nextLine();
      sendMessage(line, host, port);
      // Sleep a bit. Otherwise sunfire might get so busy
      // that it actually drops UDP packets.
      Thread.sleep(20);
    }
  }
  
  public void sendMessage(String message, String host, int port) throws Exception {
    // You can assume that a single message is shorter than 750 bytes and thus
    // fits into a single packet.
    // Implement me
      DatagramSocket senderSocket = new DatagramSocket();
      ByteBuffer bb = ByteBuffer.allocate(1024);
      bb.putInt(currentState);
      bb.put(message.getBytes());
      long checksum = computeChecksum(bb.array());
      ByteBuffer new_bb = ByteBuffer.allocate(1024);
      new_bb.putLong(checksum);
      new_bb.putInt(currentState);
      new_bb.put(message.getBytes());
      byte[] sendData = new_bb.array();
      DatagramPacket sendPkt = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(host), port);
      senderSocket.send(sendPkt);
      receiveACK(senderSocket, message, host, port);
     
  }
  
  public long computeChecksum(byte[] data) {
      CRC32 crc = new CRC32();
      crc.update(data);
      return crc.getValue();

  }
  
  public void receiveACK(DatagramSocket socket, String message, String host, int port) throws Exception {
      try {
          socket.setSoTimeout(500);
          byte[] inBuffer = new byte[1024];
          DatagramPacket rcvedPkt = new DatagramPacket(inBuffer, inBuffer.length);
          socket.receive(rcvedPkt);
          ByteBuffer bb = ByteBuffer.wrap(rcvedPkt.getData());
          long receivedCheckSum = bb.getLong();
          int sequenceNumber = bb.getInt();
          ByteBuffer sequenceNumberBuffer = ByteBuffer.allocate(4);
          sequenceNumberBuffer.putInt(sequenceNumber);
          long computedCheckSum = computeChecksum(sequenceNumberBuffer.array());
          if(computedCheckSum == receivedCheckSum) {
              if(sequenceNumber == currentState) {
                  currentState = currentState == 0? 1 : 0;
              } else {
                  sendMessage(message, host, port);
              }
              
          } else {
              sendMessage(message, host, port);
          }
      } catch(Exception e) {
          sendMessage(message, host, port);
      }
      
  }
}