package com.raonix.util;

import android.util.Log;

public class RLog
{
	public static void d(String tag, String msg)
	{
		Log.d(tag, mkMsg(msg));
	}

	public static void w(String tag, String msg)
	{
		Log.w(tag, mkMsg(msg));
	}

	public static void i(String tag, String msg)
	{
		Log.i(tag, mkMsg(msg));
	}

	public static void v(String tag, String msg)
	{
		Log.v(tag, mkMsg(msg));
	}

	public static void e(String tag, String msg)
	{
		Log.e(tag, mkMsg(msg));
	}
	
	private static String mkMsg(String msg)
	{
		if(msg==null) msg="NULL";
		String claz=Thread.currentThread().getStackTrace()[4].getClassName();
		return String.format("%s.%s:%04d %s",
				claz.substring(claz.lastIndexOf(".")+1),
				Thread.currentThread().getStackTrace()[4].getMethodName(),
				Thread.currentThread().getStackTrace()[4].getLineNumber(),
				msg);
	}
}
