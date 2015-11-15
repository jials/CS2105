import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;

public class Amy {
	String bryanIP;  // ip address of Bryan
    int bryanPort;   // port Bryan listens to
    Socket connectionSkt;  // socket used to talk to Bryan
    private ObjectOutputStream toBryan;   // to send session key to Bryan
    private ObjectInputStream fromBryan;  // to read encrypted messages from Bob
    private Crypto crypto;        // object for encryption and decryption
    // file to store received and decrypted messages
    public static final String MESSAGE_FILE = "msgs.txt";
    
    public static void main(String[] args) {

    	// Check if the number of command line argument is 2
    	if (args.length != 2) {
    		System.err.println("Usage: java Amy ByranIP BryanPort");
    		System.exit(1);
    	}

    	new Amy(args[0], args[1]);
    }
    
 // Constructor
    public Amy(String ipStr, String portStr) {
        
        this.crypto = new Crypto();
        
        try {
	        connectionSkt = new Socket(ipStr, Integer.parseInt(portStr));
	        this.toBryan = new ObjectOutputStream(this.connectionSkt.getOutputStream());
	        this.fromBryan = new ObjectInputStream(this.connectionSkt.getInputStream());
        } catch (IOException e) {
        	System.out.println("Error: Error in connecting to Bryan");
        	System.exit(1);
        }
        
        // Get Bryan's RSA public key
        receivePublicKey();
        
        // Send session key to Byran
        sendSessionKey();
        
        // Receive encrypted messages from Bryan,
        // decrypt and save them to file
        receiveMessages();
    }
    
    private void receivePublicKey() {
    	try {
			PublicKey bryanPubKey = (PublicKey)fromBryan.readObject();
			byte[] digest = (byte[])fromBryan.readObject();
			if (crypto.isValidPubKey(bryanPubKey, digest)) {
				System.out.println("Successfully retrieved Bryan's public key");
			} else {
				System.out.println("Error: MD5 Signature of Bryan's public key and given signature are different");
				System.exit(1);
			}
			
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

	// Send session key to Bryan
    public void sendSessionKey() {
        try {
			toBryan.writeObject(crypto.getSessionKey());
		} catch (IOException e) {
			System.out.println("Error: Unable to send the session key to Bryan");
		}
    }
    
    // Receive messages one by one from Bryan, decrypt and write to file
    public void receiveMessages() {
        try {
			BufferedWriter bw = new BufferedWriter(new FileWriter("msgs.txt"));
			for (int i = 0; i < 10; i++) {
				SealedObject encryptedMessageObject = (SealedObject) fromBryan.readObject();
				String msg = crypto.decryptMsg(encryptedMessageObject);
				bw.write(msg);
				bw.newLine();
			}
			bw.close();
		} catch (IOException e) {
			System.out.println("Error: Unable to write to msgs.txt");
		} catch (ClassNotFoundException e) {
			System.out.println("Error: Unable to cast the received object to SealedObject");
		}
    }
    
    /*****************/
    /** inner class **/
    /*****************/
    class Crypto {
        
        // Bryan's public key, to be read from file
        private PublicKey pubKey;
        // Amy generates a new session key for each communication session
        private SecretKey sessionKey;
        
        private PublicKey berisignPubKey;
        
        // File that contains Bob' public key
        public static final String PUBLIC_KEY_FILE = "berisign.pub";
        	
        // Constructor
        public Crypto() {
            // Read Bryan's public key from file
            readBerisignPublicKey();
            // Generate session key dynamically
            try {
				initSessionKey();
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				System.out.println("Error: cannot find the AES algorithm");
			}
        }
        
        // Read Bryan's public key from file
        public void readBerisignPublicKey() {
            // key is stored as an object and need to be read using ObjectInputStream.
            // See how Bob read his private key as an example.
        	try {
        		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(PUBLIC_KEY_FILE));
        		berisignPubKey = (PublicKey) ois.readObject();
        		ois.close();
        	} catch (IOException oie) {
                System.out.println("Error reading public key from file");
                System.exit(1);
            } catch (ClassNotFoundException cnfe) {
                System.out.println("Error: cannot typecast to class PublicKey");
                System.exit(1);
            }
        }
        
        // Generate a session key
        public void initSessionKey() throws NoSuchAlgorithmException {
            // suggested AES key length is 128 bits
        	KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        	keyGen.init(128);
        	sessionKey = keyGen.generateKey();
        }
        
        // Seal session key with RSA public key in a SealedObject and return
        public SealedObject getSessionKey() {
            
        	// RSA imposes size restriction on the object being encrypted (117 bytes).
        	// Instead of sealing a Key object which is way over the size restriction,
        	// we shall encrypt AES key in its byte format (using getEncoded() method).           
        	SealedObject sessionKeyObj = null;
            Cipher cipher;
			try {
				// Alice must use the same RSA key/transformation as Bob specified
				cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
				cipher.init(Cipher.ENCRYPT_MODE, pubKey);
				byte[] byteSessionKey = sessionKey.getEncoded();
				sessionKeyObj = new SealedObject(byteSessionKey, cipher);
			} catch (GeneralSecurityException e) {
				System.out.println("Error: Unable to encrypt the message properly");
				System.exit(1);
			} catch (IOException e) {
				System.out.println("Error: IOException");
			}
			return sessionKeyObj;
        }
        
        // Decrypt and extract a message from SealedObject
        public String decryptMsg(SealedObject encryptedMsgObj) {
            
            Object plainText = null;
            
            // Alice and Bob use the same AES key/transformation
            try {
				Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
				cipher.init(Cipher.DECRYPT_MODE, sessionKey);
				plainText = encryptedMsgObj.getObject(cipher);
			} catch (IOException e) {
				System.out.println("Error: IOException");
			} catch (GeneralSecurityException e) {
				System.out.println("Error: Unable to decrypt the message properly");
			} catch (ClassNotFoundException e) {
				System.out.println("Error: Cannot get the object");
			}
                       
            return (String) plainText;
        }
        
        public boolean isValidPubKey(PublicKey pubKey, byte[] digest) {
        	byte[] givenDigest = null;
        	byte[] decryptedDigest = null;
        	
        	String name = "bryan";
        	try {
				byte[] byteName = name.getBytes("US-ASCII");
				byte[] bytePublicKey = pubKey.getEncoded();
				MessageDigest pubKeyMd5 = MessageDigest.getInstance("MD5");
                pubKeyMd5.update(byteName);
                pubKeyMd5.update(bytePublicKey);
                givenDigest = pubKeyMd5.digest();  
                
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.DECRYPT_MODE, berisignPubKey);
                decryptedDigest = cipher.doFinal(digest);
			} catch (UnsupportedEncodingException e) {
				System.out.println("Error: US-ACSII encoding not supported");
			} catch (NoSuchAlgorithmException e) {
				System.out.println("Error: Cannot find MD5 algorithm");
			} catch (GeneralSecurityException gse) {
				System.out.println("Error: MD5 signature does not match");
                System.exit(1);
			}
        	       	
        	if (MessageDigest.isEqual(givenDigest, decryptedDigest)) {
                this.pubKey = pubKey;
                return true;
            } else {
            	return false;
            }        	
        }
    }
}
