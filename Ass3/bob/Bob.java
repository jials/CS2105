// Written by: Lifeng

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.Scanner;
import java.security.PrivateKey;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SealedObject;
import javax.crypto.spec.SecretKeySpec;

// Bob receives AES session key from Alice
// Bob reads messages line by line from file
// Bob sends encrypted messages to Alice

class Bob {  // Bob is a TCP server
    
    int port;  // port Bob listens to
    ServerSocket welcomeSkt;  // wait for Alice to connect
    Socket connectionSkt;     // socket used to talk to Alice
    private ObjectInputStream fromAlice;  // to read session key from Alice
    private ObjectOutputStream toAlice;   // to send encrypted messages to Alice
    private Crypto crypto;    // for encryption and decryption
    // file that contains messages to send
    public static final String MESSAGE_FILE = "docs.txt";
    
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
        
        this.port = Integer.parseInt(portStr);
        
        try {
            this.welcomeSkt = new ServerSocket(this.port);
        } catch (IOException ioe) {
            System.out.println("Error creating welcome socket");
            System.exit(1);
        }
        
        System.out.println("Bob listens at port " + this.port);
        
        // Create a separate socket to connect to a client
        try {
            this.connectionSkt = this.welcomeSkt.accept();
        } catch (IOException ioe) {
            System.out.println("Error creating connection socket");
            System.exit(1);
        }
        
        try {
            this.toAlice = new ObjectOutputStream(this.connectionSkt.getOutputStream());
            this.fromAlice = new ObjectInputStream(this.connectionSkt.getInputStream());
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
            this.welcomeSkt.close();
            this.connectionSkt.close();
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
            
            // Assume there are exactly 10 lines to read
            for (int i = 0; i < 10; i++) {
                String message = fromFile.nextLine();
                SealedObject encryptedMsg = this.crypto.encryptMsg(message);
                this.toAlice.writeObject(encryptedMsg);
            }
            
            fromFile.close();  // close input file stream
            System.out.println("All 10 messages are sent to Alice");
            
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
        
        // Use the same RSA private keys for all sessions.
        private PrivateKey privKey;  // private key is a secret
        // Session key is received from Alice
        private SecretKey sessionKey;
        // File that contains private key
        public static final String PRIVATE_KEY_FILE = "bob.pri";
        
        // Constructor
        public Crypto() {
            
            // read private key from file
            File privKeyFile = new File(PRIVATE_KEY_FILE);
            if ( privKeyFile.exists() && !privKeyFile.isDirectory() ) {
                readPrivateKey();
            } else {
                System.out.println("Bob cannot find RSA private key.");
                System.exit(1);
            }
        }
        
        // Receive a session key from Alice
        public void setSessionKey(SealedObject sessionKeyObj) {
            
            try {
                // getInstance(crypto algorithm/feedback mode/padding scheme)
                // Alice will use the same key/transformation
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.DECRYPT_MODE, this.privKey);
                
                // receive an AES key in "encoded form" from Alice
                byte[] rawKey = (byte[])sessionKeyObj.getObject(cipher);
                // reconstruct AES key from encoded form
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
