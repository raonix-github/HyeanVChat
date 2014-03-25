package com.raonix.util;

import java.util.HashMap;
import java.util.LinkedList;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.bytestreams.ibb.provider.CloseIQProvider;
import org.jivesoftware.smackx.bytestreams.ibb.provider.DataPacketProvider;
import org.jivesoftware.smackx.bytestreams.ibb.provider.OpenIQProvider;
import org.jivesoftware.smackx.bytestreams.socks5.provider.BytestreamsProvider;
import org.jivesoftware.smackx.filetransfer.FileTransferNegotiator;
import org.jivesoftware.smackx.provider.DiscoverInfoProvider;
import org.jivesoftware.smackx.provider.DiscoverItemsProvider;
import org.jivesoftware.smackx.provider.StreamInitiationProvider;

import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;

public class XmppClient
{
	private static final String LOG_TAG = new String("XmppClient");
	private static final int DEFAULT_PORT=5222;
	
    private XMPPConnection mConnection;
    
    private LinkedList<Observer> mObservers=new LinkedList<Observer>();
    
//    private String mHost;
//    private int mPort;
//    private String mService;

    private Status mStatus=Status.STATUS_INITIAL;
    private Error mError=Error.ERR_OK;
    private String mErrorMessage="";
    
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    

    public enum Status
    {
    	STATUS_INITIAL,
    	STATUS_DISCONNECTED,
    	STATUS_DISCONNECTING,
    	STATUS_CONNECTING,
    	STATUS_CONNECTED,
    	STATUS_AUTHENTICATING,
    	STATUS_AUTHENTICATED
    }
    
    public enum Error
    {
    	ERR_OK,
    	ERR_CONNECTION_FAIL,
    	ERR_AUTHENTICATION_FAIL,
    	ERR_NOT_CONNECTED,
    	ERR_ALREADY_LOGIN,
    	ERR_INTERNAL
    }
    
    
    
    
    public interface Observer
    {
    	public void onMessage(String from, String msg, XmppClient client, String resource);
    	public void onStateChanged(XmppClient.Status status);
    	public void onUserStateChanged(String user, boolean available);
    }
   
    
    
    
    
    public XmppClient()
    {
		XMPPConnection.DEBUG_ENABLED=false;

    	mHandlerThread=new HandlerThread("XmppClientMainThread");
    	mHandlerThread.start();
    	mHandler=new Handler(mHandlerThread.getLooper());
    }
    
    public XmppClient(String host, int port, String service, String user, String passwd )
    {
    	this();
    	
    	connect(host,port,service);
    	login(user,passwd);
    }
    
    public void setStatusAvailable(String status_msg)
    {
    	if(mConnection!=null&&mConnection.isAuthenticated())
    	{
    		Presence p = new Presence(Presence.Type.available);
    		if(status_msg==null) p.setStatus("Hyean(iUnplug)");
    		else p.setStatus(status_msg);
    		mConnection.sendPacket(p);
    	}
    }

    public void connect(final String host, final int port, final String service)
    {
    	if(host==null||port<1||service==null)
    	{
    		RLog.w(LOG_TAG, "Can not connect.");
    		RLog.w(LOG_TAG, "Invalied argument.");
    		return;
    	}

    	mHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					_connect(host, port, service);
				}
			});
    }
    
    
    private void _connect(String host,int port, String service)
    {
    	_setState(Status.STATUS_CONNECTING);

    	if(host==null) host="talk.google.com";
    	if(port<1||port>32767) port=DEFAULT_PORT;
    	if(service==null) service="gmail.com";

    	RLog.d(LOG_TAG, "Host:"+host+" Port:"+port+" Service:"+service);

    	ConnectionConfiguration connConfig =
    			new ConnectionConfiguration(host, port, service);

    	connConfig.setSASLAuthenticationEnabled(true);
    	connConfig.setSecurityMode(SecurityMode.required);
    	connConfig.setRosterLoadedAtLogin(true);
    	connConfig.setReconnectionAllowed(true);

    	XMPPConnection connection = new XMPPConnection(connConfig);

    	try
    	{
    		// You have to put this code before you
    		// login(Required for Gtalk and not for Jabber.org)
    		SASLAuthentication.supportSASLMechanism("PLAIN", 0);

    		connection.connect();

    		mConnection=connection;
    	}
    	catch(XMPPException e)
    	{
    		e.printStackTrace();
    		_setState(Status.STATUS_DISCONNECTED,Error.ERR_CONNECTION_FAIL,e.getMessage());
    	}
    }

    
    public boolean isConnected()
    {
    	return (mStatus==Status.STATUS_CONNECTED ||
    			mStatus==Status.STATUS_AUTHENTICATING ||
    			mStatus==Status.STATUS_AUTHENTICATED);
    }

    public void login(String userid, String passwd)
    {
    	login(userid,passwd,false);
    }

    public void login(final String userid, final String passwd, final boolean relogin)
    {

    	if(mConnection==null||userid==null||passwd==null)
    	{
    		RLog.w(LOG_TAG, "Can not login.");
    		if(mConnection==null)
    			RLog.w(LOG_TAG, "Client not connected.");
    		else
    			RLog.w(LOG_TAG, "Invalied argument.");

    		return;
    	}

    	if(relogin)
    	{
    		mHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						try
						{
							_logout();
							Thread.sleep(1000);
							_login(userid, passwd);
						}
						catch(InterruptedException e)
						{
							e.printStackTrace();
						}
					}
				});
    	}
    	else
    	{
    		mHandler.post(new Runnable()
    		{
    			@Override
    			public void run()
    			{
    				_login(userid, passwd);
    			}
    		});
    	}
    }
   
    public boolean isLogin()
    {
    	return (mStatus==Status.STATUS_AUTHENTICATED);
    }

   
    
    
    
    
    
    private void _logout()
    {
    	if(mStatus!=Status.STATUS_AUTHENTICATED)
    		return;

    	_setState(Status.STATUS_DISCONNECTING);

    	mConnection.disconnect();
    	while(mConnection.isConnected())
    	{
    		try
    		{
    			Thread.sleep(300);
    		}
    		catch(InterruptedException e)
    		{
    			e.printStackTrace();
    		}
    	}

    	_setState(Status.STATUS_DISCONNECTED);
    }
    
    
    
    
    private void _login(String userid, String passwd)
    {
   		Status stb=mStatus;
    	if(mStatus==Status.STATUS_DISCONNECTED)
    	{
    		if(mConnection==null)
    		{
    			_setError(Error.ERR_NOT_CONNECTED, null);
    			return;
    		}

    		try
    		{
    			stb=mStatus;
    			_setState(Status.STATUS_CONNECTING);
    			mConnection.connect();
    			_setState(Status.STATUS_CONNECTED);
    		}
    		catch(XMPPException e)
    		{
    			_setState(stb,Error.ERR_CONNECTION_FAIL,null);
    			return;
    		}
    	}

    	try
    	{
   			stb=mStatus;
    		_setState(Status.STATUS_AUTHENTICATING);
    		mConnection.login(userid, passwd, "hyean_xmpp");

    		if(mConnection.isAuthenticated())
    		{
    			// 파일(이미지) 전송을 위한 내용.
    			ProviderManager pm=ProviderManager.getInstance();

    			pm.addIQProvider("query","http://jabber.org/protocol/disco#info", new DiscoverInfoProvider());
    			pm.addIQProvider("query", "http://jabber.org/protocol/bytestreams", new BytestreamsProvider());
    			pm.addIQProvider("si", "http://jabber.org/protocol/si", new StreamInitiationProvider());

    			// 아래 pm 관련 설정내용은 정확하지 않음. 불필요한것이 존재 할 수 있슴.
    			pm.addIQProvider("query","http://jabber.org/protocol/disco#items", new DiscoverItemsProvider());
    			pm.addIQProvider("open", "http://jabber.org/protocol/ibb", new OpenIQProvider());
    			pm.addIQProvider("data", "http://jabber.org/protocol/ibb", new DataPacketProvider());
    			pm.addIQProvider("close", "http://jabber.org/protocol/ibb", new CloseIQProvider());
    			pm.addExtensionProvider("data", "http://jabber.org/protocol/ibb", new DataPacketProvider());

    			mConnection.addPacketListener(mPresenceListener,
    					new PacketTypeFilter(Presence.class));
//    			FileTransferNegotiator.setServiceEnabled(mConnection, true);
    			mConnection.addConnectionListener(mConnctionListener);


    			// Set the status to available
    			Presence presence = new Presence(Presence.Type.available);
    			mConnection.sendPacket(presence);

    			// Add a packet listener to get messages sent to us
    			PacketFilter filter = new MessageTypeFilter(Message.Type.chat);
    			mConnection.addPacketListener(new XMPPPacketListener(), filter);

    			_setState(Status.STATUS_AUTHENTICATED);
    		}
    	}
    	catch (XMPPException ex)
    	{
   			_setState(stb,Error.ERR_AUTHENTICATION_FAIL, null);
   			ex.printStackTrace();
    	}
    }
    

    private ConnectionListener mConnctionListener = new ConnectionListener()
    {

    	@Override
    	public void reconnectionSuccessful()
    	{
    		RLog.i(LOG_TAG, "Reconnection successful.");
    	}

    	@Override
    	public void reconnectionFailed(Exception ex)
    	{
    		RLog.i(LOG_TAG, "Reconnection failed.");
    	}

    	@Override
    	public void reconnectingIn(int arg0)
    	{
    		RLog.i(LOG_TAG, "Reconnecting in "+arg0+".");
    	}

    	@Override
    	public void connectionClosedOnError(Exception ex)
    	{
    		RLog.i(LOG_TAG, "Connection closed on error.");
    		ex.printStackTrace();
    	}

    	@Override
    	public void connectionClosed()
    	{
    		mHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						mConnection.disconnect();
						_setState(Status.STATUS_DISCONNECTED);
					}
				});
    	}
    };
    

    private class XMPPPacketListener implements PacketListener
    {
    	@Override
    	public void processPacket(Packet pkt)
    	{
    		final Message message = (Message) pkt;
    		if (message.getBody() != null)
    		{
    			String fromName = StringUtils.parseBareAddress(message.getFrom());
    			String fromResource = StringUtils.parseResource(message.getFrom());

//    			RLog.v( LOG_TAG, "Got message ["
//    					+ message.getBody() + "] from ["
//    					+ fromName + "] resource ["
//    					+ fromResource + "]");     
    			
    			new MessageSender(
    					fromName, message.getBody(), XmppClient.this, fromResource).start();
    		}
    	}
    }
    
    private class MessageSender extends Thread
    	{
    		private String name;
    		private String msg;
    		private XmppClient client;
    		private String resource;
    		
    		public MessageSender(String fromName, String message, XmppClient xmppClient, String fromResource)
    		{
    			name=fromName;
    			msg=message;
    			client=xmppClient;
    			resource=fromResource;
    		}

    		@Override
    		public void run()
    		{
    			synchronized (mObservers)
    			{
    				for(Observer o : mObservers)
    				{
    					o.onMessage(name, msg, client, resource);
    				}
    			}
    		}
    	}
    
    
    // buddys format <user id(email)>:<user name>
    private void _applyBuddy(final String[] buddys, final int retry)
    {
    	if(!isLogin()||retry<0) return;

    	Roster roster=null;
    	
    	try
    	{
    		roster=mConnection.getRoster();
    		if(roster==null)
    			mHandler.post(new Runnable()
    				{
    					@Override
    					public void run()
    					{
    						_applyBuddy(buddys, retry-1);
    					}
    				});
    	}
    	catch(Exception ex)
    	{
    		ex.printStackTrace();
    		mHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						_applyBuddy(buddys, retry-1);
					}
				});
    	}

    	// 서버목록중 설정에 없는것 삭제.
    	for( RosterEntry entry : roster.getEntries() )
    	{
    		boolean exist = false;
    		for(String buddy : buddys)
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
    			RLog.d(LOG_TAG, "Remove buddy:"+entry.getUser());
    			Presence p = new Presence(Presence.Type.unsubscribed);
    			p.setTo(entry.getUser());
    			try
    			{
    				mConnection.sendPacket(p);   
    				roster.removeEntry(entry);
    			}
    			catch (XMPPException e)
    			{
    				e.printStackTrace();
    				RLog.e(LOG_TAG, "Buddy sync failure.");
    				return;
    			}	
    		}
    	}


    	// 설정목록중 서버에 없는것 추가.
    	for(String buddy : buddys)
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

    		if(!exist)
    		{
    			RLog.d(LOG_TAG, "Regist buddy:"+elements[1]);
    			Presence p = new Presence(Presence.Type.subscribed);
    			p.setTo(elements[1]);

    			try
    			{
    				mConnection.sendPacket(p);   
    				roster.createEntry(elements[1],elements[0],null);
    			}
    			catch (XMPPException e)
    			{
    				e.printStackTrace();
    				RLog.e(LOG_TAG, "Buddy sync failure.");
    				return;
    			}	
    		}
    	}

    }
    
//    public void setStatusAvailable(String status_msg)
//    {
//    	if(mConnection!=null&&mConnection.isAuthenticated())
//    	{
//    		Presence p = new Presence(Presence.Type.available);
//    		if(status_msg==null) p.setStatus("Hyean(iUnplug)");
//    		else p.setStatus(status_msg);
//    		mConnection.sendPacket(p);
//    	}
//    }
//
//	public void setStatusUnavailable()
//	{
//    	if(mConnection!=null&&mConnection.isAuthenticated())
//    	{
//    		Presence p = new Presence(Presence.Type.unavailable);
//    		mConnection.sendPacket(p);
//    	}
//	}
    
    
    public boolean sendMessage(final String user_to, final String message)
    {
    	if(!isLogin())
    	{
    		RLog.w(LOG_TAG, "Oops! You are not currently logged in.\nDo not send message:" + message);
    		return false;
    	}

    	mHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					Message msgObj = new Message(user_to, Message.Type.chat);
					msgObj.setBody(message);
					mConnection.sendPacket(msgObj);    	
				}
			});
    	
    	return true;
    }
    
    
    public boolean removeBuddy(String user_who)
    {
    	if(!isLogin())
    	{
    		RLog.w(LOG_TAG, "Oops! You are not currently logged in.");
    		return false;
    	}
    	
    	Roster roster = mConnection.getRoster();
    	for( RosterEntry entry : roster.getEntries() )
    	{
    		if( entry.getUser().compareTo(user_who) == 0 )
    		{
    			try
				{
					roster.removeEntry(entry);
					RLog.i(LOG_TAG, "Remove buddy: "+entry.getUser());
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
    	if(!isLogin())
    	{
    		RLog.w(LOG_TAG, "Oops! You are not currently logged in.");
    		return false;
    	}
    	
    	Roster roster = mConnection.getRoster();
    	try
		{
			roster.createEntry(user_new,name,groups);
			RLog.i(LOG_TAG, "Add buddy: "+user_new);
		}
		catch (XMPPException e)
		{
			e.printStackTrace();
			return false;
		}
    	
    	return true;
    }
    
    
    
    
    
    public void addObserver( Observer observer )
    {
    	synchronized (mObservers)
		{
    		if(!mObservers.contains(observer))
    		{
    			mObservers.add(observer);
    		}
		}
    }

    public void removeObserver( Observer observer )
    {
    	synchronized (mObservers)
		{
    		if(mObservers.contains(observer))
    		{
    			mObservers.remove(observer);
    		}
		}
    }
    
    
    
    
    
    public void disconnect()
    {
    	if(mConnection!=null)
    	{
    		if( mConnection.isConnected() )
    		{
    			mConnection.disconnect();
    		}
    		
    		mConnection=null;
    	}
    }
    	

    
    
    
    
	private HashMap<String,LinkedList<String>> mPeerResources =
			new HashMap<String,LinkedList<String>>();

	public String[] getPeerResources(String peerName)
	{
		if(peerName==null||TextUtils.isEmpty(peerName)) return new String[0];
		LinkedList<String> res=mPeerResources.get(peerName);
		if(res==null||res.isEmpty()) return new String[0];
		return (String[]) res.toArray();
	}

	
	private class PresenceAnouncer implements Runnable
	{
		private Presence presence;
		public PresenceAnouncer(Presence presence)
		{
			this.presence=presence;
		}

		@Override
		public void run()
		{
			synchronized (mObservers)
			{
				for(Observer o:mObservers)
				{
					String fromName=
							StringUtils.parseBareAddress(presence.getFrom());
					// Presence.Type 은 다른것도 존재함.
					if(presence.getType()==Presence.Type.available)
						o.onUserStateChanged(fromName,true);
					else if(presence.getType()==Presence.Type.unavailable)
						o.onUserStateChanged(fromName,false);
				}
			}
		}
	}

	private PacketListener mPresenceListener = new PacketListener()
		{
			@Override
			public void processPacket(Packet packet)
			{
				Presence presence=(Presence)packet;
				String from=presence.getFrom();

				String fromName = StringUtils.parseBareAddress(from);
				if(fromName==null||TextUtils.isEmpty(fromName))
				{
					RLog.i(LOG_TAG, "FromName not found.");
					return;
				}

				// fromName 이 없을경우 상태변경을 알릴 필요 없슴.
				mHandler.post(new PresenceAnouncer(presence));

				String fromResource = StringUtils.parseResource(from);
				if(fromResource==null||TextUtils.isEmpty(fromResource))
				{
					RLog.i(LOG_TAG, "FromResource not found.");
					return;
				}

				LinkedList<String> res=mPeerResources.get(fromName);
				if(res==null)
				{
					res=new LinkedList<String>();
					mPeerResources.put(fromName, res);
				}


				if (presence.getType() == null || presence.getType() == Presence.Type.available)
				{
					if(!res.contains(fromResource))
					{
						RLog.i(LOG_TAG,"Add resource:"+fromResource);
						res.add(fromResource);
					}
				}
				else
				{
					if(res.contains(fromResource))
					{
						RLog.i(LOG_TAG,"Remove resource:"+fromResource);
						res.remove(fromResource);
					}
				}

			}
		};
				

    public boolean isUserAvailable(String user)
    {
    	if(isLogin())
    	{
    		Roster roster=mConnection.getRoster();
    		if(roster!=null)
    		{
    			Presence presence=roster.getPresence(user);
    			if(presence!=null)
    			{
    				return (presence.getType()==Presence.Type.available);
    			}
    		}
    	}
    	return false;
    }
				
				
				
	public Status getStatus()
	{
		return mStatus;
	}
				
	public Error getError()
	{
		return mError;
	}
	
	public String getErrorMessage()
	{
		return mErrorMessage;
	}

	
	
	
	
	private void _setState(Status st)
	{
		_setState(st,null,null);
	}
	
	private void _setError(Error err, String msg)
	{
		_setState(null,err,msg);
	}
				
	synchronized private void _setState(Status st, Error err, String msg)
	{
		Status stOld=mStatus;
		if(st!=null)
			mStatus=st;
		if(err!=null)
			mError=err;
		if(msg==null)
			if(err!=null)
				msg=err.toString();
		mErrorMessage=msg;
		
		if(stOld!=mStatus)
		{
			for(Observer o:mObservers)
			{
				o.onStateChanged(mStatus);
			}
		}
	}
}
