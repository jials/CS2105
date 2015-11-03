// Author: 

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import javax.crypto.SealedObject;

/************************************************
  * This skeleton program is prepared for weak  *
  * and average students.                       *
  * If you are very strong in programming. DIY! *
  * Feel free to modify this program.           *
  ***********************************************/

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
        
    }
    
    // Receive messages one by one from Bob, decrypt and write to file
    public void receiveMessages() {
        
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
        }
        
        // Generate a session key
        public void initSessionKey() {
            // suggested AES key length is 128 bits
        }
        
        // Seal session key with RSA public key in a SealedObject and return
        public SealedObject getSessionKey() {
            
            // Alice must use the same RSA key/transformation as Bob specified
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            
            // RSA imposes size restriction on the object being encrypted (117 bytes).
            // Instead of sealing a Key object which is way over the size restriction,
            // we shall encrypt AES key in its byte format (using getEncoded() method).           
        }
        
        // Decrypt and extract a message from SealedObject
        public String decryptMsg(SealedObject encryptedMsgObj) {
            
            String plainText = null;
            
            // Alice and Bob use the same AES key/transformation
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            
            
            return plainText;
        }
    }
}