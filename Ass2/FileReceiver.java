/**
 * Meta-data packet (total size: 1000 bytes)
 * 8 bytes: Checksum (long)
 * 4 bytes: Sequence number (int)
 * 255 bytes: Destination File Path (String) 
 * 8 bytes: File Size (long)
 * 
 * Data packet (total size: 1000 bytes)
 * 8 bytes: Checksum (long)
 * 4 bytes: Sequence number (int)
 * 4 bytes: attached data size (int)
 * 1500-8-4-4 bytes: data (byte[])
 * 
 * ACK packet 
 * 8 bytes: Checksum (long)
 * 4 bytes: Sequence number (int)
 */
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.zip.CRC32;

public class FileReceiver {
	//All the units are in BYTES
	public static final int SIZE_CHECKSUM = 8;
	//http://stackoverflow.com/questions/6571435/limit-on-file-name-length-in-bash
	//Maximum number of characters is 255. Each character in UTF-8 is 1 byte
	public static final int SIZE_DEST_FPATH = 255;
	public static final int SIZE_FILESIZE = 8;
	public static final int SIZE_SEQNUM = 4;
	public static final int SIZE_ATTACHEDDATASIZE = 4;
	public static final int SIZE_DATA_PACKET = 1000;
	public static final int SIZE_METADATA_PACKET = SIZE_CHECKSUM + SIZE_DEST_FPATH + SIZE_FILESIZE + SIZE_SEQNUM;
	public static final int SIZE_DATA = SIZE_DATA_PACKET - SIZE_CHECKSUM - SIZE_SEQNUM - SIZE_ATTACHEDDATASIZE;
	public static final int SIZE_ACK_PACKET = SIZE_CHECKSUM + SIZE_SEQNUM;
	// Maximum number of positive integers that can be represented by int (2^31 - 1)
	// but sunfire doesnt allow this. Use 2500000 instead.
	public static final int MAX_SEQNUM = 2500000; 
	
	private InetSocketAddress addr;
	private DatagramSocket sk;
	private DatagramPacket pkt;
	private CRC32 crc;
	private ByteBuffer packetBuffer;
	private BufferedOutputStream bos;
	private SocketAddress sourceSocketAddress;
	
	private static Logger log;
	private static byte[] incomingPacket;
	private static boolean[] packetReceived;
	private static String destFilePath;
	private static long destFileSize;
	private static int accumulatedDataSize;
	
	public FileReceiver (int incomingPort) {
		log = Logger.getLogger(FileReceiver.class.getName());
		packetReceived = new boolean[MAX_SEQNUM]; 
		try {
			sk = new DatagramSocket(incomingPort);
		} catch (SocketException e) {
			System.out.println("Unable to create a socket at port: " + incomingPort);
		}
		crc = new CRC32();
		incomingPacket = new byte[SIZE_DATA_PACKET]; 
		packetBuffer = ByteBuffer.wrap(incomingPacket);
		pkt = new DatagramPacket(incomingPacket, SIZE_DATA_PACKET);
		accumulatedDataSize = 0;
	}
	
	public void waitForPacket() {
		while (true) {
			pkt.setLength(SIZE_DATA_PACKET); 
			
			try {
				sk.receive(pkt);
			} catch (IOException e) {
				System.out.println("Unable to receive packets from DatagramSocket!");
				break;
			}
			
			sourceSocketAddress = pkt.getSocketAddress();
			
			if ((pkt.getLength() != SIZE_METADATA_PACKET) && (pkt.getLength() != SIZE_DATA_PACKET)) {
				log.fine("pkt too short");
				continue;
			}
			
			packetBuffer.rewind();
			long chksum = packetBuffer.getLong();
			int seqNum = packetBuffer.getInt();
			
			crc.reset();
			crc.update(incomingPacket, 8, pkt.getLength()-8);
			
			if (crc.getValue() != chksum) {
				log.fine("Pkt corrupt");
			} else {
				log.fine("Pkt " + seqNum);
				// The packet contains metadata
				if (seqNum == 0) {
					getMetaData();
				} else {
					getData(seqNum);
					if (accumulatedDataSize >= destFileSize) {
						System.out.println("Finish transmitting the file: " + accumulatedDataSize);
						try {
							bos.close();
						} catch (IOException e) {
							System.out.println("Failed to close BufferedOutputStream");
						}
						break;
					}
				}
			}	
		}
	}
	
	public void getMetaData() {
		//return ack despite received the packet already. 
		if (packetReceived[0]) {
			sendAck(0);
			return;
		}
		
		byte[] byteArrayDestFilePath = new byte[SIZE_DEST_FPATH];	
		packetBuffer.get(byteArrayDestFilePath, 0, byteArrayDestFilePath.length);
		destFilePath = new String(byteArrayDestFilePath);
		destFilePath = destFilePath.trim();
		destFileSize = packetBuffer.getLong();
		log.fine("=============Receiving Meta-Data=============");
		log.fine("Filename: " + destFilePath + " Size: " + destFileSize);
		
		try {
			bos = new BufferedOutputStream(new FileOutputStream(destFilePath));
			packetReceived[0] = true;
			sendAck(0);	
		} catch (FileNotFoundException e) {
			System.out.println("Cannot write at the destinated filepath!");
		}
	}
	
	public void getData(int seqNum) {
		if (packetReceived[seqNum]) {
			sendAck(seqNum);
			return;
		}
		
		int attachedDataSize = packetBuffer.getInt();
		
		byte[] attachedData = new byte[attachedDataSize];
		packetBuffer.get(attachedData, 0, attachedDataSize);
		try {
			bos.write(attachedData);
			bos.flush();
			accumulatedDataSize += attachedDataSize;
			packetReceived[seqNum] = true;
			sendAck(seqNum);
		} catch (IOException e) {
			System.out.println("Unable to write data!");
		}
		
		log.fine("Pkt " + seqNum + " received with size: " + attachedDataSize + " -- " + accumulatedDataSize);
	}
	
	public void sendAck(int currentSeqNum) {
		byte[] ackByteArray = new byte[SIZE_ACK_PACKET];
		ByteBuffer ackBuffer = ByteBuffer.wrap(ackByteArray);
		ackBuffer.clear();
		// reserve space for checksum
		ackBuffer.putLong(0);
		ackBuffer.putInt(currentSeqNum);
		
		crc.reset();
		crc.update(ackByteArray, 8, ackByteArray.length-8);
		long chksum = crc.getValue();
		ackBuffer.rewind();
		ackBuffer.putLong(chksum);
		
		DatagramPacket ack = new DatagramPacket(ackByteArray, SIZE_ACK_PACKET, sourceSocketAddress);
		try {
			sk.send(ack);
		} catch (IOException e) {
			System.out.println("Unable to send Ack " + currentSeqNum);
		}
	}
	
	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Usage: FileReceiver <incoming_port>");
			System.exit(-1);
		}
		
		int port = Integer.parseInt(args[0]);
		FileReceiver fr = new FileReceiver(port);
		fr.waitForPacket();		
	}
	
	// Obtained from skeleton code
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes, int len) {
	    char[] hexChars = new char[len * 2];
	    for ( int j = 0; j < len; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
}
