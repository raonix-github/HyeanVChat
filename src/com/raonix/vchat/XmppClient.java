package com.raonix.vchat;

import java.util.ArrayList;

import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket.ItemStatus;
import org.jivesoftware.smack.util.StringUtils;

import android.util.Log;

public class XmppClient {
	private static final String TAG = new String("XmppClient");
	
    private XMPPConnection m_connection;
    private OnMessageListener mMessageListener;
    private String m_host;
    private int m_port;
    private String m_service;
    private String m_username;
    private String m_password;
    
    public interface OnMessageListener {
    	public void onMessage(String from, String msg, XmppClient client);
    }
   
    public XmppClient() {
    	this(null, 0, null, null, null);
    }
    
    public XmppClient(String host, int port, String service, String user, String passwd ) {
    	setHost(host,port,service);
    	setUser(user,passwd);
    }
    
    public void setHost(String host, int port, String service) {
    	if(host==null) host="talk.google.com";
    	if(port<1||port>32767) port=5222;
    	if(service==null) service="gmail.com";
    	m_host = host;
    	m_port = port;
    	m_service = service;
    	
    	Log.d(TAG, "Host:"+m_host+" Port:"+m_port+" Service:"+m_service);
    }
    
    public void setUser(String username, String passwd) {
    	// TODO
    	if(username==null) m_username="local.iunplug.co.kr@gmail.com";
    	else m_username = username;

    	if(passwd==null) m_password="iunplug1234";
    	else m_password=passwd;
    	
    	Log.d(TAG, "User:"+m_username+" Password:"+m_password);
    }
    
    public boolean isConnected() {
    	if(m_connection!=null) {
    		return m_connection.isAuthenticated();
    	} else {
    		return false;
    	}
    }
    
    public void connect(boolean reconnect) {
    	if(reconnect&&isConnected()) disconnect();
    	else new Thread(new RunConnecting()).start();
    }
   
    private class RunConnecting implements Runnable {
		@Override
		public void run() {
			ConnectionConfiguration connConfig =
					new ConnectionConfiguration(m_host, m_port, m_service);
			XMPPConnection connection = new XMPPConnection(connConfig);

			try {
				connection.connect();
				Log.i(TAG, "Connected to " + connection.getHost());
				m_connection = connection;

				connection.login(m_username, m_password);
				Log.i(TAG, "Logged in as " + connection.getUser());

				if(m_connection.isAuthenticated()) {
					m_connection.addConnectionListener(m_connction_listener);

					// Set the status to available
					Presence presence = new Presence(Presence.Type.available);
					connection.sendPacket(presence);

					// Add a packet listener to get messages sent to us
					PacketFilter filter = new MessageTypeFilter(Message.Type.chat);
					connection.addPacketListener(new XMPPPacketListener(), filter);
				}
			}
			catch (XMPPException ex) {
				if(m_connection!=null) {
					// connection success but login failed
					try {
						Log.e(TAG, "Login failed. (as:"+m_username+")");
						m_connection.disconnect();
					} catch(Exception e) {
						e.printStackTrace();
					}
				} else {
					Log.e(TAG, "Connection failed. (to:"+connection.getHost()+")");
				}

				ex.printStackTrace();
				m_connection = null;
			}
		}
    };
    
    private static final String LOG_TAG_CONNECTION = "ConnectionListener";
    private ConnectionListener m_connction_listener = new ConnectionListener() {

    	@Override
    	public void reconnectionSuccessful() {
    		Log.i(LOG_TAG_CONNECTION, "Reconnection successful.");
    	}

    	@Override
    	public void reconnectionFailed(Exception ex) {
    		Log.i(LOG_TAG_CONNECTION, "Reconnection failed.");
    	}

    	@Override
    	public void reconnectingIn(int arg0) {
    		Log.i(LOG_TAG_CONNECTION, "Reconnecting in "+arg0+".");
    	}

    	@Override
    	public void connectionClosedOnError(Exception ex) {
    		Log.i(LOG_TAG_CONNECTION, "Connection closed on error.");
    		ex.printStackTrace();
    	}

    	@Override
    	public void connectionClosed() {
    		Log.i(LOG_TAG_CONNECTION, "Connection closed.");
    	}
    };

    private class XMPPPacketListener implements PacketListener {
    	@Override
    	public void processPacket(Packet pkt) {
    		final Message message = (Message) pkt;
    		if (message.getBody() != null) {
    			String fromName = StringUtils.parseBareAddress(message .getFrom());
    			Log.v( TAG, "Got message [" + message.getBody() + "] from [" + fromName + "]");     
    			if( mMessageListener != null ) {
    				mMessageListener.onMessage(fromName, message.getBody(), XmppClient.this);
    			}
    		}
    	}
    }
    
    
    private synchronized void syncBuddy() {
    	if(isConnected()) {
    		new Thread(new RunSyncBuddy()).start();
    	}
    }
    
    private class RunSyncBuddy implements Runnable
    {
		@Override
		public void run()
		{
			try
			{
				Roster roster=null;
				int retry=3;
				while(retry>0)
				{
					if(isConnected())
					{
						try
						{
							roster = m_connection.getRoster();
							if(roster==null)
							{
								Log.e(TAG, "Roster is null.");
								return; ///< 널인경우 종료.
							}
							break; ///< 정상인경우 while loop 를 빠져나감.
						}
						catch(Exception ex)
						{
							ex.printStackTrace();
						}
					}

					try
					{
						Thread.sleep(1000);
					}
					catch (InterruptedException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					--retry; ///< 재시도.
					if(retry==0) return; ///< 재시도 한계점에서는 종료.
					Log.i(TAG, "Retry sync buddy list.");
				}

				ArrayList<String> listconfig = new ArrayList<String>();
				// TODO
				listconfig.add("remote.iunplug.co.kr@gmail.com");
				
				Log.v(TAG, "Group count:"+roster.getGroupCount());
				for(RosterGroup rg : roster.getGroups())
				{
					Log.v(TAG, "Groud:"+rg.getName());
				}
				
				Log.v(TAG, "User count:"+roster.getEntryCount());
				for( RosterEntry entry : roster.getEntries() )
				{
					Log.v(TAG, "User:"+entry.getUser());
				}
				
				// 서버목록중 설정에 없는것 삭제.
				for( RosterEntry entry : roster.getEntries() )
				{
					boolean exist = false;
					for(String buddy : listconfig)
					{
						String[] elements = buddy.split(":");
						if(elements==null||elements.length!=2)
							continue;
						
						if( entry.getUser().equals(elements[1]) )
						{
							exist = true;
							break;
						}
					}
					
					if(!exist)
					{
						Log.d(TAG, "Remove buddy:"+entry.getUser());
						Presence p = new Presence(Presence.Type.unsubscribe);
						p.setTo(entry.getUser());
						m_connection.sendPacket(p);   
						
						roster.removeEntry(entry);
					}
				}

				
				// 설정목록중 서버에 없는것 추가.
				for(String buddy : listconfig)
				{
					boolean exist = false;
					String[] elements = buddy.split(":");
					if(elements==null||elements.length!=2)
						continue;

					for( RosterEntry entry : roster.getEntries() )
					{
						if( entry.getUser().equals(elements[1]) )
						{
							exist = true;
							break;
						}
					}

					// 설정목록중 서버에 없는것 추가.
					if(!exist)
					{
						Log.d(TAG, "Regist buddy:"+elements[1]);
						roster.createEntry(elements[1],elements[0],null);
						Presence p = new Presence(Presence.Type.subscribe);
						p.setTo(elements[1]);
						m_connection.sendPacket(p);   
					}
				}

				setStatusAvailable(null);
			}
			catch (XMPPException e)
			{
				e.printStackTrace();
				Log.e(TAG, "Buddy sync failure.");
			}	
		}
    };
    
    public void setStatusAvailable(String status_msg)
    {
    	if(m_connection!=null&&m_connection.isAuthenticated())
    	{
    		Presence p = new Presence(Presence.Type.available);
    		if(status_msg==null) p.setStatus("Hyean(iUnplug)");
    		else p.setStatus(status_msg);
    		m_connection.sendPacket(p);
    	}
    }

	public void setStatusUnavailable()
	{
    	if(m_connection!=null&&m_connection.isAuthenticated())
    	{
    		Presence p = new Presence(Presence.Type.unavailable);
    		m_connection.sendPacket(p);
    	}
	}
    
    
    public boolean sendMessage(String user_to, String message)
    {
    	if(!isConnected())
    	{
    		Log.w(TAG, "Oops! You are not currently logged in.\nDo not send message:" + message);
    		return false;
    	}

    	Message msgObj = new Message(user_to, Message.Type.chat);
    	msgObj.setBody(message);
    	
    	m_connection.sendPacket(msgObj);    	
    	return true;
    }
    
    public boolean removeBuddy(String user_who)
    {
    	if(!isConnected())
    	{
    		Log.w(TAG, "Oops! You are not currently logged in.");
    		return false;
    	}
    	
    	Roster roster = m_connection.getRoster();
    	for( RosterEntry entry : roster.getEntries() )
    	{
    		if( entry.getUser().compareTo(user_who) == 0 )
    		{
    			try
				{
					roster.removeEntry(entry);
					Log.i(TAG, "Remove buddy: "+entry.getUser());
					return true;
				}
				catch (XMPPException e)
				{
					e.printStackTrace();
					return false;
				}
    		}
    	}
    	return true;
    }

    
    public boolean addBuddy(String user_new, String name, String[] groups)
    {
    	if(!isConnected())
    	{
    		Log.w(TAG, "Oops! You are not currently logged in.");
    		return false;
    	}
    	
    	Roster roster = m_connection.getRoster();
    	try
		{
			roster.createEntry(user_new,name,groups);
			Log.i(TAG, "Add buddy: "+user_new);
		}
		catch (XMPPException e)
		{
			e.printStackTrace();
			return false;
		}
    	
    	return true;
    }
    
    
    public void setOnMessageListener( OnMessageListener listener )
    {
    	mMessageListener = listener;
    }
    
    
    public void disconnect()
    {
    	if(m_connection!=null)
    	{
    		if( m_connection.isConnected() )
    		{
    			m_connection.disconnect();
    		}
    		
    		m_connection=null;
    	}
    }
}
