/**
 * 
 */
package cafe.kagu.keyauth;

import java.io.File;

import cafe.kagu.keyauth.utils.HwidUtils;

/**
 * @author lavaflowglow
 *
 */
public class Example {

	/**
	 * Program entry point
	 * @param args Program args
	 */
	public static void main(String[] args) {
		
		// Example keyauth object
		KeyAuth keyAuth = new KeyAuth("ZvEPlLo1aX", "KaguTest",
				"f1bb6072a0c259c5f628555b52837da2ae917b22201aab57f59c00a29b1f5ad5", 1.0);
		
		// Init keyauth
		keyAuth.initialize(msg -> {
			System.out.println("Failed " + msg);
		}, msg -> {
			System.out.println("Error " + msg);
		}, msg -> {
			System.out.println("Tampered response " + msg);
		});
		
		keyAuth.checkBlacklist(msg -> {
			System.out.println("Error " + msg);
		}, msg -> {
			System.out.println("Tampered response " + msg);
		}, msg -> {
			System.out.println("Blacklisted " + msg);
		});
		
		try {
			keyAuth.download("660273", new File("test.png"), msg -> {
				System.out.println(msg);
			}, msg -> {
				System.out.println(msg);
			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Register new user account
		keyAuth.register("Test Account", "Example Password", "e621-QVZG0-BBIIA-FTITL-ZM4QT", msg -> {
			System.out.println("Error " + msg);
		}, msg -> {
			System.out.println("Tampered response " + msg);
		}, msg -> {
			System.out.println("Error registering account " + msg);
		}, msg -> {
			System.out.println("Successfully registered account " + msg);
		});
		
		// Login to an existing account
		keyAuth.login("Test Account", "Example Password", msg -> {
			System.out.println("Error " + msg);
		}, msg -> {
			System.out.println("Tampered response " + msg);
		}, msg -> {
			System.out.println("Error logging in " + msg);
		}, msg -> {
			System.out.println("Successfully logged in " + msg);
		});
		
		try {
			keyAuth.download("660273", new File("test.png"), msg -> {
				System.out.println(msg);
			}, msg -> {
				System.out.println(msg);
			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		keyAuth.ban(msg -> {
//			System.out.println(msg);
//		}, msg -> {
//			System.out.println(msg);
//		});
		
		keyAuth.log("Test log", msg -> {
			System.out.println(msg);
		}, msg -> {
			System.out.println(msg);
		});
		
	}

}
