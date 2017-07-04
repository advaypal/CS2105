import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.util.zip.CRC32;



class Receiver {
    
  private int currentState = 1;  
  public static void main(String[] args) throws Exception {
    // check if the number of command line argument is 4
    if (args.length != 1) {
      System.out.println("Usage: java Receiver <port>");
      System.exit(1);
    }
    new Receiver(Integer.parseInt(args[0]));
  }
  
  public Receiver(int port) throws Exception {
    // Do not change this
    DatagramSocket socket = new DatagramSocket(port);
    while (true) {
      String message = receiveMessage(socket);
      if(message != null) {
          System.out.println(message);
      }
    }
  }
  
  public String receiveMessage(DatagramSocket socket) throws Exception {
      byte[] inBuffer = new byte[1024];
      DatagramPacket rcvedPkt = new DatagramPacket(inBuffer, inBuffer.length);
      socket.receive(rcvedPkt);
      ByteBuffer bb = ByteBuffer.wrap(rcvedPkt.getData());
      long receivedCheckSum = bb.getLong(0);
      int sequenceNumber = bb.getInt(8);
      byte[] messageData = new byte[bb.remaining() - 12];
      for (int i = 0; i < bb.remaining() - 12; i++) {
          messageData[i] = bb.get(i+12); 
      }
      String x = new String(messageData);
      ByteBuffer new_bb = ByteBuffer.allocate(1024);
      new_bb.putInt(sequenceNumber);
      new_bb.put(messageData);
      String data = null;
      long computedCheckSum = computeChecksum(new_bb.array());
      if(computedCheckSum == receivedCheckSum) {
          if((sequenceNumber == 0 && currentState == 1) || (sequenceNumber == 1 && currentState == 0) ) {
              currentState = sequenceNumber;
              data = new String(messageData);
          }
          
      }
      sendAck(socket, rcvedPkt.getAddress(), rcvedPkt.getPort());
      return data;
      
  }
  
  public long computeChecksum(byte[] data) {
      CRC32 crc = new CRC32();
      crc.update(data);
      return crc.getValue();

  }
  
  public void sendAck(DatagramSocket socket, InetAddress host, int port) throws IOException {
      ByteBuffer bb = ByteBuffer.allocate(1024);
      ByteBuffer sequenceNumber = ByteBuffer.allocate(4);
      sequenceNumber.putInt(currentState);
      bb.putLong(computeChecksum(sequenceNumber.array()));
      bb.putInt(currentState);
      byte[] sendData = bb.array();
      DatagramPacket ackPkt = new DatagramPacket(sendData, sendData.length, host, port);
      socket.send(ackPkt);
  }
  
}