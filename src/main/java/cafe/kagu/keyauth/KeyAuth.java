/**
 * 
 */
package cafe.kagu.keyauth;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.commons.codec.binary.Hex;
import org.json.JSONObject;

import cafe.kagu.keyauth.utils.HashingUtils;
import cafe.kagu.keyauth.utils.HwidUtils;
import cafe.kagu.keyauth.utils.ResponseHandler;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * @author DistastefulBannock This class is used to store data, make requests,
 *         and give a developer easy to use methods to interface with keyauths
 *         api
 */
public class KeyAuth {

	/**
	 * @param ownerId   You can find out the owner id in the profile settings on
	 *                  keyauth.win
	 * @param appName   Application name
	 * @param appSecret The app secret
	 * @param version   Application version
	 */
	public KeyAuth(String ownerId, String appName, String appSecret, double version) {
		this(ownerId, appName, appSecret, version + "");
	}

	/**
	 * @param ownerId   You can find out the owner id in the profile settings on
	 *                  keyauth.win
	 * @param appName   Application name
	 * @param appSecret The app secret
	 * @param version   Application version
	 */
	public KeyAuth(String ownerId, String appName, String appSecret, String version) {
		this.ownerId = ownerId;
		this.appName = appName;
		this.appSecret = appSecret;
		this.version = version;
	}

	private String ownerId, appName, appSecret, version, session = null;
	private final String guid = getRandomGuid();
	private boolean loggedIn = false;
	public static final String KEYAUTH_ENDPOINT = "https://keyauth.win/api/1.2/";
	private final OkHttpClient client = new OkHttpClient();

	/**
	 * Initializes keyauth
	 * 
	 * @param onFailedInit     A ResponseHandler containing the code that runs if
	 *                         the response doesn't allow us to initialize. This
	 *                         could show an error message, close the app, log the
	 *                         error. Whatever you need it to do
	 * @param requestError     A ResponseHandler containing the code that runs if
	 *                         there is an error while sending the request
	 * @param tamperedResponse A ResponseHandler containing the code that runs if
	 *                         the response from the server is tampered with
	 */
	public void initialize(ResponseHandler onFailedInit, ResponseHandler requestError,
			ResponseHandler tamperedResponse) {
		if (session != null) {
			return;
		}

		// Create body for request
		FormBody formBody = new FormBody.Builder().add("type", "init").add("ver", version).add("name", appName)
				.add("ownerid", ownerId).add("enckey", guid).build();

		// Create and send the request
		Request request = new Request.Builder().url(KEYAUTH_ENDPOINT).post(formBody)
				.addHeader("Content-Type", "application/x-www-form-urlencoded").build();

		Call call = client.newCall(request);
		Response response = null;
		try {
			response = call.execute();
		} catch (IOException e) {
			if (response != null)
				response.close();
			e.printStackTrace();
			requestError.run("IOException");
			return;
		}

		// Docs say that the response can only ever be 200, if this isn't the case then
		// something has gone wrong
		if (response.code() != 200) {
			requestError.run("Response Code " + response.code());
			response.close();
			return;
		}

		// Get response body
		String signature = response.header("signature"); // For later, getting before response is closed
		String jsonStr = null;
		try {
			jsonStr = response.body().string();
			response.close();
		} catch (IOException e) {
			response.close();
			e.printStackTrace();
			requestError.run("IOException");
			return;
		}

		// Verify the response isn't tampered with
		String hash = HashingUtils.hashHmacSha256(appSecret, jsonStr);
		if (!signature.equals(hash)) {
			tamperedResponse.run("Signature header \"" + signature + "\" didn't match \"" + hash + "\"");
			return;
		}

		// Parse and handle response
		JSONObject json = new JSONObject(jsonStr);
		if (json.getBoolean("success")) {
			session = json.getString("sessionid");
		} else {
			if (json.getString("message").equalsIgnoreCase("invalidver")) {
				try {
					Desktop.getDesktop().browse(URI.create(json.getString("download")));
				} catch (Exception e) {
					onFailedInit.run("Failed to open update link, closing program");
				}
				System.exit(0);
			}else {
				onFailedInit.run(json.getString("message"));
			}
		}
	}

	/**
	 * Registers a new account for the user
	 * 
	 * @param username                   Their username
	 * @param password                   Their password
	 * @param key                        Their license key
	 * @param requestError               A ResponseHandler containing the code that
	 *                                   runs if there is an error while sending the
	 *                                   request
	 * @param tamperedResponse           A ResponseHandler containing the code that
	 *                                   runs if the response from the server is
	 *                                   tampered with
	 * @param errorRegisteringAccount    A ResponseHandler containing the code that
	 *                                   runs if there is an issue while registering
	 *                                   an account
	 * @param successfullyCreatedAccount A ResponseHandler containing the code that
	 *                                   runs if the account is successfully created
	 */
	public void register(String username, String password, String key, ResponseHandler requestError,
			ResponseHandler tamperedResponse, ResponseHandler errorRegisteringAccount,
			ResponseHandler successfullyCreatedAccount) {
		if (session == null) {
			requestError.run("Not initialized");
			return;
		} else if (loggedIn) {
			requestError.run("Aleady logged in");
			return;
		}

		FormBody formBody = new FormBody.Builder().add("type", "register").add("username", username)
				.add("pass", password).add("key", key).add("hwid", HwidUtils.getHwid()).add("sessionid", session)
				.add("name", appName).add("ownerid", ownerId).build();

		String jsonStr = makeRequest(formBody);
		switch (jsonStr) {
			case "IOException":
			case "NON200": {
				requestError.run(jsonStr);
			}break;
			case "Tampered": {
				tamperedResponse.run(jsonStr);
			}break;
			default: {
				JSONObject json = new JSONObject(jsonStr);
				if (json.getBoolean("success")) {
					checkSession(requestError, tamperedResponse, errorRegisteringAccount);
					successfullyCreatedAccount.run(jsonStr);
				} else {
					errorRegisteringAccount.run(json.getString("message"));
				}
			}break;
		}

	}

	/**
	 * Logins into an account for the user
	 * 
	 * @param username             Their username
	 * @param password             Their password
	 * @param requestError         A ResponseHandler containing the code that runs
	 *                             if there is an error while sending the request
	 * @param tamperedResponse     A ResponseHandler containing the code that runs
	 *                             if the response from the server is tampered with
	 * @param errorLoggingIn       ResponseHandler containing the code that runs if
	 *                             there is an issue while logging into the account
	 * @param successfullyLoggedIn A ResponseHandler containing the code that runs
	 *                             if the user is successfully logged in
	 */
	public void login(String username, String password, ResponseHandler requestError, ResponseHandler tamperedResponse,
			ResponseHandler errorLoggingIn, ResponseHandler successfullyLoggedIn) {
		if (session == null) {
			requestError.run("Not initialized");
			return;
		} else if (loggedIn) {
			requestError.run("Aleady logged in");
			return;
		}

		FormBody formBody = new FormBody.Builder().add("type", "login").add("username", username).add("pass", password)
				.add("hwid", HwidUtils.getHwid()).add("sessionid", session).add("name", appName).add("ownerid", ownerId)
				.build();

		String jsonStr = makeRequest(formBody);
		switch (jsonStr) {
			case "IOException":
			case "NON200": {
				requestError.run(jsonStr);
			}break;
			case "Tampered": {
				tamperedResponse.run(jsonStr);
			}break;
			default: {
				JSONObject json = new JSONObject(jsonStr);
				if (json.getBoolean("success")) {
					checkSession(requestError, tamperedResponse, errorLoggingIn);
					successfullyLoggedIn.run(jsonStr);
				} else {
					errorLoggingIn.run(json.getString("message"));
				}
			}break;
		}

	}

	/**
	 * Checks if the current session is that of a logged in user, is they are then
	 * it overwrites the current session used
	 * 
	 * @param requestError     A ResponseHandler containing the code that runs if
	 *                         there is an error while sending the request
	 * @param tamperedResponse A ResponseHandler containing the code that runs if
	 *                         the response from the server is tampered with
	 * @param errorLoggingIn   ResponseHandler containing the code that runs if
	 *                         there is an issue while logging into the account
	 */
	public void checkSession(ResponseHandler requestError, ResponseHandler tamperedResponse,
			ResponseHandler errorLoggingIn) {
		if (session == null) {
			requestError.run("Not initialized");
			return;
		}

		FormBody formBody = new FormBody.Builder().add("type", "check").add("sessionid", session).add("name", appName)
				.add("ownerid", ownerId).build();

		String jsonStr = makeRequest(formBody);
		switch (jsonStr) {
			case "IOException":
			case "NON200": {
				requestError.run(jsonStr);
			}break;
			case "Tampered": {
				tamperedResponse.run(jsonStr);
			}break;
			default: {
				JSONObject json = new JSONObject(jsonStr);
				if (json.getBoolean("success")) {
					loggedIn = true;
				} else {
					errorLoggingIn.run(json.getString("message"));
				}
			}break;
		}

	}

	/**
	 * Checks if the hwid or the ip of the user is blacklisted
	 * 
	 * @param requestError     A ResponseHandler containing the code that runs if
	 *                         there is an error while sending the request
	 * @param tamperedResponse A ResponseHandler containing the code that runs if
	 *                         the response from the server is tampered with
	 * @param blacklisted      ResponseHandler containing the code that runs if the
	 *                         user/hwid is blacklisted
	 */
	public void checkBlacklist(ResponseHandler requestError, ResponseHandler tamperedResponse,
			ResponseHandler blacklisted) {
		if (session == null) {
			requestError.run("Not initialized");
			return;
		}
		
		FormBody formBody = new FormBody.Builder().add("type", "checkblacklist").add("hwid", HwidUtils.getHwid())
				.add("sessionid", session).add("name", appName).add("ownerid", ownerId).build();

		String jsonStr = makeRequest(formBody);
		switch (jsonStr) {
			case "IOException":
			case "NON200": {
				requestError.run(jsonStr);
			}break;
			case "Tampered": {
				tamperedResponse.run(jsonStr);
			}break;
			default: {
				JSONObject json = new JSONObject(jsonStr);
				if (json.getBoolean("success")) {
					blacklisted.run(json.getString("message"));
				}
			}break;
		}

	}

	/**
	 * Downloads a file from the file id
	 * @param fileId The id of the file to download
	 * @param downloadFile The file where the downloaded data should be stored
	 * @param requestError A ResponseHandler containing the code that runs if there is an error while sending the request
	 * @param tamperedResponse A ResponseHandler containing the code that runs if the response from the server is tampered with
	 * @throws Exception Thrown when something goes wrong
	 */
	public void download(String fileId, File downloadFile, ResponseHandler requestError, ResponseHandler tamperedResponse) throws Exception {
		if (session == null) {
			requestError.run("Not initialized");
			return;
		}
		
		FormBody formBody = new FormBody.Builder().add("type", "file").add("fileid", fileId)
				.add("sessionid", session).add("name", appName).add("ownerid", ownerId).build();
		
		String jsonStr = makeRequest(formBody);
		switch (jsonStr) {
			case "IOException":
			case "NON200":{
				requestError.run(jsonStr);
			}break;
			case "Tampered":{
				tamperedResponse.run(jsonStr);
			}break;
			default:{
				JSONObject json = new JSONObject(jsonStr);
				if (json.getBoolean("success")){
					
					// Download file
					FileOutputStream fos = new FileOutputStream(downloadFile);
					fos.write(Hex.decodeHex(json.getString("contents").toCharArray()));
					fos.close();
					
				}else {
					requestError.run(json.getString("message"));
				}
			}break;
		}
		
	}
	
	/**
	 * Bans the current logged in user, it will also blacklist their hwid and their current ip
	 * @param requestError A ResponseHandler containing the code that runs if there is an error while sending the request
	 * @param tamperedResponse A ResponseHandler containing the code that runs if the response from the server is tampered with
	 */
	public void ban(ResponseHandler requestError, ResponseHandler tamperedResponse) {
		if (session == null) {
			requestError.run("Not initialized");
			return;
		} else if (!loggedIn) {
			requestError.run("Not logged in");
			return;
		}
		
		FormBody formBody = new FormBody.Builder().add("type", "ban").add("hwid", HwidUtils.getHwid())
				.add("sessionid", session).add("name", appName).add("ownerid", ownerId).build();
		
		String jsonStr = makeRequest(formBody);
		switch (jsonStr) {
			case "IOException":
			case "NON200":{
				requestError.run(jsonStr);
			}break;
			case "Tampered":{
				tamperedResponse.run(jsonStr);
			}break;
			default:{
				JSONObject json = new JSONObject(jsonStr);
				if (!json.getBoolean("success")){
					requestError.run(json.getString("message"));
				}
			}break;
		}
		
	}
	
	/**
	 * Sends a log request to the auth server
	 * @param message The message to log
	 * @param requestError A ResponseHandler containing the code that runs if there is an error while sending the request
	 * @param tamperedResponse A ResponseHandler containing the code that runs if the response from the server is tampered with
	 */
	public void log(String message, ResponseHandler requestError, ResponseHandler tamperedResponse) {
		if (session == null) {
			requestError.run("Not initialized");
			return;
		}
		
		String pcName = null;
		try {
			pcName = InetAddress.getLocalHost().getHostName();
			pcName += "-" + System.getProperty("user.name");
		} catch (Exception e) {
			requestError.run("Couldn't get pc name");
			return;
		}
		
		FormBody formBody = new FormBody.Builder().add("type", "log").add("pcuser", pcName).add("message", message)
				.add("sessionid", session).add("name", appName).add("ownerid", ownerId).build();
		
		String jsonStr = makeRequest(formBody);
		switch (jsonStr) {
			case "IOException":
			case "NON200":{
				requestError.run(jsonStr);
			}break;
			case "Tampered":{
				tamperedResponse.run(jsonStr);
			}break;
			default:{
				
			}break;
		}
		
	}
	
	/**
	 * Makes a request to the auth server and checks it for tampering, the
	 * initialize method doesn't use this because the tamper check is slightly
	 * different for that response
	 * 
	 * @param requestBody The request payload to send
	 * @return The response json
	 */
	private String makeRequest(RequestBody requestBody) {
		// Create and send the request
		Request request = new Request.Builder().url(KEYAUTH_ENDPOINT).post(requestBody)
				.addHeader("Content-Type", "application/x-www-form-urlencoded").build();

		Call call = client.newCall(request);
		Response response = null;
		try {
			response = call.execute();
		} catch (IOException e) {
			if (response != null)
				response.close();
			e.printStackTrace();
			return "IOException";
		}

		// Docs say that the response can only ever be 200, if this isn't the case then
		// something has gone wrong
		if (response.code() != 200) {
			response.close();
			return "NON200";
		}

		// Get response body
		String signature = response.header("signature"); // For later, getting before response is closed
		String jsonStr = null;
		try {
			jsonStr = response.body().string();
			response.close();
		} catch (IOException e) {
			response.close();
			e.printStackTrace();
			return "IOException";
		}

		// Verify the response isn't tampered with
		String hash = HashingUtils.hashHmacSha256(guid + "-" + appSecret, jsonStr);
		if (!jsonStr.isEmpty() && !signature.equals(hash)) {
			return "Tampered";
		}

		// Return
		return jsonStr;
	}

	/**
	 * @return the loggedIn
	 */
	public boolean isLoggedIn() {
		return loggedIn;
	}

	/**
	 * Generates and returns a random guid
	 * 
	 * @return A new random guid
	 */
	private String getRandomGuid() {
		String guid = UUID.randomUUID().toString();
		if (guid.length() > 35) {
			guid = guid.substring(0, 35);
		}
		return guid;
	}
	
	/**
	 * @return the appName
	 */
	public String getAppName() {
		return appName;
	}
	
	/**
	 * @return the ownerId
	 */
	public String getOwnerId() {
		return ownerId;
	}
	
	/**
	 * @return the version
	 */
	public String getVersion() {
		return version;
	}
	
	/**
	 * @return the session
	 */
	public String getSession() {
		return session;
	}
	
}
