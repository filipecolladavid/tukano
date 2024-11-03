package utils;

import java.util.Arrays;

public class Hex {
	static final String hexits = "0123456789ABCDEF";
	
	public static String of( byte[] data ) {
		return of( data, data.length);
	}
	
	public static String of( byte[] data, int len ) {
		var sb = new StringBuilder();
		for ( var b : Arrays.copyOf(data, len) ) {
			sb.append( hexits.charAt( (b & 0xF0 ) >>> 4 ));;
			sb.append( hexits.charAt( (b & 0x0F ) ));
		}		
		return sb.toString();
	}

	public static byte[] toBytes(String hex) {
		var bytes = new byte[hex.length() / 2];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
		}
		return bytes;
	}
}
