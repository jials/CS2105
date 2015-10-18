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
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.zip.CRC32;

public class FileSender {
	//All the units are in BYTES
	public static final int SIZE_CHECKSUM = 8;
	//http://stackoverflow.com/questions/6571435/limit-on-file-name-length-in-bash
	//Maximum number of characters is 255. Each character in UTF-8 is 1 byte
	public static final int SIZE_SEQNUM = 4;
	public static final int SIZE_DEST_FPATH = 255;
	public static final int SIZE_FILESIZE = 8;
	public static final int SIZE_ATTACHEDDATASIZE = 4;
	public static final int SIZE_DATA_PACKET = 1000; // Limitation of UnreliNet
	public static final int SIZE_METADATA_PACKET = SIZE_CHECKSUM + SIZE_DEST_FPATH + SIZE_FILESIZE + SIZE_SEQNUM;
	public static final int SIZE_DATA = SIZE_DATA_PACKET - SIZE_CHECKSUM - SIZE_SEQNUM - SIZE_ATTACHEDDATASIZE;
	public static final int SIZE_ACK_PACKET = SIZE_CHECKSUM + SIZE_SEQNUM;
	
	private InetSocketAddress addr;
	private DatagramSocket sk;
	private DatagramPacket pkt;
	private CRC32 crc;
	private ByteBuffer dataBuffer, headerBuffer;
	private byte[] headerByteArray, dataByteArray;
	private BufferedInputStream sourceBufferedInputStream;
	
	private static Logger log;
	private static int seqNum;
	private static long sourceFileSize;
	private static String srcFilePath, destFilePath;
	
	public FileSender (String host, int port, String srcFpath, String destFpath) throws SocketException {
		addr = new InetSocketAddress(host, port);
		sk = new DatagramSocket();
		crc = new CRC32();
		
		log = Logger.getLogger(FileSender.class.getName());
		
		srcFilePath = srcFpath;
		destFilePath = destFpath;
		
		try {
			// http://stackoverflow.com/questions/3122422/usage-of-bufferedinputstream
			File sourceFile = new File(srcFpath);
			sourceFileSize = sourceFile.length();
			sourceBufferedInputStream = new BufferedInputStream(new FileInputStream(sourceFile));
		} catch (FileNotFoundException e) {
			System.out.println("File not found!");
		}
	}
	
	public void sendMetaData() throws IOException {
		headerByteArray = new byte[SIZE_METADATA_PACKET]; 
		headerBuffer = ByteBuffer.wrap(headerByteArray);
		seqNum = 0; // Initialized to 0 
		
		headerBuffer.clear();
		// reserve space for checksum
		headerBuffer.putLong(0);
		headerBuffer.putInt(seqNum);
		byte[] byteArrayDestFilePath = destFilePath.getBytes("UTF-8");
		// Add empty spaces to the extra space to ensure smooth reading at receiver side.
		headerBuffer.put(new byte[SIZE_DEST_FPATH - byteArrayDestFilePath.length]); 
		headerBuffer.put(byteArrayDestFilePath);
		headerBuffer.putLong(sourceFileSize);
		
		crc.reset();
		crc.update(headerByteArray, 8, headerByteArray.length-8);
		long chksum = crc.getValue();
		headerBuffer.rewind();
		headerBuffer.putLong(chksum);

		pkt = new DatagramPacket(headerByteArray, headerByteArray.length, addr);
		
		// Debug output
		log.fine("Send the following file: " + srcFilePath);
		log.fine("=============Sending Meta-Data=============");
		log.fine("Sent CRC:" + chksum);
		sk.send(pkt);
		
		waitAck(seqNum, pkt);
	}
	
	public void sendData() {
		dataByteArray = new byte[SIZE_DATA_PACKET]; 
		dataBuffer = ByteBuffer.wrap(dataByteArray);
		seqNum = 1; // Initialized to 1
		
		int numBytesRead = 0;
		byte[] data = new byte[SIZE_DATA];
		log.fine("=============Sending Data=============");
		
		// While there are still bytes to be read, -1 signifies end of stream
		try {
			while ((numBytesRead = sourceBufferedInputStream.read(data)) != -1) {
				dataBuffer.clear();
				// reserve space for checksum
				dataBuffer.putLong(0);
				dataBuffer.putInt(seqNum);
				dataBuffer.putInt(numBytesRead);
				dataBuffer.put(data, 0, data.length); // TODO: try
				
				crc.reset();
				crc.update(dataByteArray, 8, dataByteArray.length-8);
				long chksum = crc.getValue();
				dataBuffer.rewind();
				dataBuffer.putLong(chksum);
				
				pkt = new DatagramPacket(dataByteArray, dataByteArray.length, addr);
				
				// Debug output
				log.fine("Packet " + seqNum + " with data of size: " + numBytesRead);
				log.fine("Sent CRC:" + chksum);
				
				try {
					sk.send(pkt);
					waitAck(seqNum, pkt);			
					seqNum += 1;
				} catch (IOException e) {
					System.out.println("Unable to send pkt from the DatagramSocket!");
				}
			}
		} catch (IOException e) {
			System.out.println("Unable to read data from BufferedInputStream!");
		}
	}
	
	private void waitAck(int seqNum, DatagramPacket pkt) throws IOException {
		sk.setSoTimeout(1); 
		boolean isAck = false;
		
		while(!isAck) {
			byte[] ackByteArray = new byte[SIZE_ACK_PACKET];
			ByteBuffer ackBuffer = ByteBuffer.wrap(ackByteArray);
			DatagramPacket ackPkt = new DatagramPacket(ackByteArray, SIZE_ACK_PACKET);
			ackPkt.setLength(SIZE_ACK_PACKET);
			try {
				sk.receive(ackPkt);
				if (ackPkt.getLength() < SIZE_ACK_PACKET) {
					log.fine("Corrupted Packet due to shorter length!");
					continue;
				} else {
					ackBuffer.rewind();
					long chksum = ackBuffer.getLong();
					int ackSeqNum = ackBuffer.getInt();
					crc.reset();
					crc.update(ackByteArray, 8, ackPkt.getLength()-8);
					if (crc.getValue() != chksum) {
						log.fine("ack packet " + seqNum + " is corrupted!");
						continue;
					} else {
						if (ackSeqNum == seqNum) {
							isAck = true;
							log.fine("ACK " + seqNum);
						} 
					}
				}
				
			} catch (SocketTimeoutException e) {
				//System.out.println("Socket time out for ACK: " + seqNum);
				sk.send(pkt);
				// Recursive call
				//waitAck(seqNum, pkt);
				sk.setSoTimeout(1);
			}
		}
	}
	
	public static void main(String[] args) {
		if (args.length != 4) {
			System.err.println("Usage: FileSender <host> <port> <src_file> <dest_file>");
			System.exit(-1);
		}
		
		String host = args[0];
		int port = Integer.parseInt(args[1]);
		String srcFilePath = args[2];
		String destFilePath = args[3];
		
		FileSender fs;
		try {
			fs = new FileSender(host, port, srcFilePath, destFilePath);
			fs.sendMetaData();
			fs.sendData();
		} catch (SocketException e) {
			System.out.println("Socket Exception!");
		} catch (IOException e) {
			System.out.println("IO Exception!");
		}
	}
	
	// Obtained from skeleton code
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
}
