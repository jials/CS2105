// Author: jiale

package alice;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;

// Alice knows Bob's public key
// Alice sends Bob session (AES) key
// Alice receives messages from Bob, decrypts and saves them to file

class Alice {  // Alice is a TCP client
    
    String bobIP;  // ip address of Bob
    int bobPort;   // port Bob listens to
    Socket connectionSkt;  // socket used to talk to Bob
    private ObjectOutputStream toBob;   // to send session key to Bob
    private ObjectInputStream fromBob;  // to read encrypted messages from Bob
    private Crypto crypto;        // object for encryption and decryption
    // file to store received and decrypted messages
    public static final String MESSAGE_FILE = "msgs.txt";
    
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
        
        // Send session key to Bob
        sendSessionKey();
        
        // Receive encrypted messages from Bob,
        // decrypt and save them to file
        receiveMessages();
    }
    
    // Send session key to Bob
    public void sendSessionKey() {
        try {
			toBob.writeObject(crypto.getSessionKey());
		} catch (IOException e) {
			System.out.println("Error: Unable to send the session key to Bob");
		}
    }
    
    // Receive messages one by one from Bob, decrypt and write to file
    public void receiveMessages() {
        try {
			BufferedWriter bw = new BufferedWriter(new FileWriter("msgs.txt"));
			for (int i = 0; i < 10; i++) {
				SealedObject encryptedMessageObject = (SealedObject) fromBob.readObject();
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
        
        // Bob's public key, to be read from file
        private PublicKey pubKey;
        // Alice generates a new session key for each communication session
        private SecretKey sessionKey;
        // File that contains Bob' public key
        public static final String PUBLIC_KEY_FILE = "bob.pub";
        
        // Constructor
        public Crypto() {
            // Read Bob's public key from file
            readPublicKey();
            // Generate session key dynamically
            try {
				initSessionKey();
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				System.out.println("Error: cannot find the AES algorithm");
			}
        }
        
        // Read Bob's public key from file
        public void readPublicKey() {
            // key is stored as an object and need to be read using ObjectInputStream.
            // See how Bob read his private key as an example.
        	try {
        		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(PUBLIC_KEY_FILE));
        		this.pubKey = (PublicKey) ois.readObject();
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
    }
}