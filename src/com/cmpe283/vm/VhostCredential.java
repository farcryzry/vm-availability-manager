package com.cmpe283.vm;

import java.util.HashMap;

public class VhostCredential {
	public static final String USER_NAME = "root";
	public static final String PASSWORD = "12!@qwQW";
	@SuppressWarnings("serial")
	public static final HashMap<String, String> SSL_MAP = new HashMap<String, String>() {
		{
			put("130.65.132.151", "68:68:84:51:62:6E:DB:D1:2E:8B:F7:35:B0:B0:0E:60:43:7F:17:43");
			put("130.65.132.155", "EE:2B:25:8F:48:6E:38:5C:B3:DD:B0:87:FD:66:AA:1B:25:DF:B9:7C");
			put("130.65.132.159", "43:51:66:B8:3C:76:F5:8F:9A:63:90:0D:13:2C:25:B8:48:64:2D:6F");
		}
	};
}
