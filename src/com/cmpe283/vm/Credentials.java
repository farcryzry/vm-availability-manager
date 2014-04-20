package com.cmpe283.vm;

import java.util.HashMap;

public class Credentials {
	public static final String ROOT_VCENTER_URL = "https://130.65.132.13/sdk";
	public static final String VCENTER_URL = "https://130.65.132.103/sdk";
	public static final String VCENTER_USER_NAME = "administrator";
	public static final String PASSWORD = "12!@qwQW";
	public static final String VHOST_USER_NAME = "root";
	@SuppressWarnings("serial")
	public static final HashMap<String, String> VHOST_SSL_MAP = new HashMap<String, String>() {
		{
			put("130.65.132.151", "68:68:84:51:62:6E:DB:D1:2E:8B:F7:35:B0:B0:0E:60:43:7F:17:43");
			put("130.65.132.155", "EE:2B:25:8F:48:6E:38:5C:B3:DD:B0:87:FD:66:AA:1B:25:DF:B9:7C");
			put("130.65.132.159", "43:51:66:B8:3C:76:F5:8F:9A:63:90:0D:13:2C:25:B8:48:64:2D:6F");
		}
	};
	@SuppressWarnings("serial")
	public static final HashMap<String, String> VHOST_NAME_MAP = new HashMap<String, String>() {
		{
			put("130.65.132.151", "t03-vHost01-cum1-lab1 _.132.151");
			put("130.65.132.155", "t03-vHost01-cum1-proj1_132.155");
			put("130.65.132.159", "t03-vHost01-cum1-lab2_132.159");
		}
	};
}
