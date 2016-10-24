/*******************************************************************************
 * Copyright (c) 2009, 2016 GreenVulcano ESB Open Source Project.
 * All rights reserved.
 *
 * This file is part of GreenVulcano ESB.
 *
 * GreenVulcano ESB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GreenVulcano ESB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GreenVulcano ESB. If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package it.greenvulcano.gvesb.vulcon.bus.connectors;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.karaf.config.core.ConfigRepository;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.greenvulcano.configuration.XMLConfig;
import it.greenvulcano.configuration.XMLConfigException;
import it.greenvulcano.gvesb.vulcon.bus.BusLink;
import it.greenvulcano.gvesb.vulcon.bus.connectors.GVBusConnector;

public class GVBusLink implements BusLink, ExceptionListener{
	private final static Logger LOG = LoggerFactory.getLogger(GVBusLink.class);

	private static final AtomicReference<String> busId = new AtomicReference<>();
	
	private ConnectionFactory connectionFactory;
	private Connection connection;
	private Session session;
	
	private ConfigRepository configRepository;
	private final Timer timer = new Timer();
	private final static Set<GVBusConnector> connectors;
	
	static {
		connectors = Collections.synchronizedSet(new LinkedHashSet<>());
	}
		
	public void setBusId(String busId) {		
		GVBusLink.busId.set(busId);		
	}
	
	public static String getBusId(){
		return GVBusLink.busId.get();
	}
		
	public void setConfigRepository(ConfigRepository configRepository) {
		this.configRepository = configRepository;
	}
	
	public void setConnectionFactory(ConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}
		
	public void setConnectors(Set<GVBusConnector> connectors) {			
		GVBusLink.connectors.addAll(connectors);
	}
	
	public void init() {
		String busId = GVBusLink.busId.get();
		if (busId != null && !busId.trim().isEmpty() && !busId.equals("undefined")){
			try {				
				LOG.debug("Connection to "+busId);
				createSession();
			} catch (JMSException e) {
				LOG.error("Error connecting session on queue "+busId, e);
			}
		}
	}
	
	public void destroy(){
		LOG.debug("Destroing session on bus ");
		if (session!=null) {
			try {
				timer.cancel();
				session.close();
				connection.close();
			} catch (JMSException jmsException) {
				LOG.error("Error realeasing session on queue "+busId, jmsException);
			}
		}
		
	}	
	
	@Override
	public String connect(String busId) throws IOException, GeneralSecurityException {
	
		String message = "Bus connection established";		
		try {
			@SuppressWarnings("unchecked")
			Dictionary<String, Object> gvesbCfg = configRepository.getConfigProperties("it.greenvulcano.gvesb.bus");
			
			String currentKey = (String) gvesbCfg.get("gvbus.apikey");
										
			if (Objects.isNull(currentKey)||currentKey.equalsIgnoreCase("undefined") ) {
				setBusId(busId);
				
				createSession();			
				createUserFolder();
				
				gvesbCfg.put("gvbus.apikey", validKey(busId));
				configRepository.update("it.greenvulcano.gvesb.bus", gvesbCfg);
			} else {
				throw new GeneralSecurityException("Bus connection already exists");
			}
									
			configRepository.update("it.greenvulcano.gvesb.bus", gvesbCfg);
						
		} catch (IOException | InvalidSyntaxException | JMSException e) {
			LOG.error("Error connecting session on queue "+busId, e);
			
			if (session!=null) {
				try {
					session.close();
				} catch (JMSException jmsException) {
					LOG.error("Error disconnecting session on queue "+busId, jmsException);
				}
			}
			
			throw new IOException("Bus connection failed: " + e.getMessage());
		} 
		
		return message;
		
	}
	
	@Override
	public String disconnect(String busId) throws IOException, GeneralSecurityException {
		String message = "Bus " + busId+" disconnected";
			
		try {
			@SuppressWarnings("unchecked")
			Dictionary<String, Object> gvesbCfg = configRepository.getConfigProperties("it.greenvulcano.gvesb.bus");
			
			String currentKey = (String) gvesbCfg.get("gvbus.apikey");			
							
			if (validKey(currentKey).equalsIgnoreCase(validKey(busId)) ) {
				setBusId(null);
				message = "Bus "+gvesbCfg.remove("gvbus.apikey")+ " disconnected";
				configRepository.update("it.greenvulcano.gvesb.bus", gvesbCfg);
				
				String defaultConfigPath = System.getProperty("gv.app.home") + File.separator + XMLConfig.DEFAULT_FOLDER;
				XMLConfig.setBaseConfigPath(defaultConfigPath);
				XMLConfig.reloadAll();
				
				if (session!=null) {
					try {
						session.close();
					} catch (JMSException jmsException) {
						LOG.error("Error disconnecting session on queue "+busId, jmsException);
					}
				}
			} else {
				throw new GeneralSecurityException("API key mismatch");
			}
						
		} catch (IOException | InvalidSyntaxException | XMLConfigException e) {
			LOG.error("Error connecting session on queue "+busId, e);
			
			if (session!=null) {
				try {
					session.close();
				} catch (JMSException jmsException) {
					LOG.error("Error disconnecting session on queue "+busId, jmsException);
				}
			}
			
			throw new IOException("Bus connection failed: " + e.getMessage(), e);
		} 
		
		return message;
		
	}
	
	private String validKey(String key) {
		if (key!=null && !key.trim().isEmpty() && !key.equalsIgnoreCase("undefined")) {
			return key;
		} else {
			throw new IllegalArgumentException("Invalid key "+key);
		}
	}
	
	private void createSession() throws JMSException {						
			
			if (session!=null) {
				session.close();
			}
			
			try {				
				connection = connectionFactory.createConnection();
			} catch (JMSException jmsException) {
				LOG.error("Fail to create a JMS connection");
				onException(jmsException);
				throw jmsException;
			}
			
			connection.setExceptionListener(this);
			connection.start();
			
			LOG.debug("JMS connection started ");
			session  =  connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			LOG.debug("JMS session created ");
			String busId = GVBusLink.busId.get();
			synchronized (connectors) {
				for (GVBusConnector connector : connectors) {					
					connector.connect(session, busId);
				}	
			}					
			
			notifyConnection();		
	}
	
	private void createUserFolder(){
		String defaultConfigPath = System.getProperty("gv.app.home") + File.separator + XMLConfig.DEFAULT_FOLDER;
		String configurationPath = System.getProperty("gv.app.home") + File.separator + busId;
		try {
			
			File defaultConfigDir = new File(defaultConfigPath);			
			File configDir = new File(configurationPath);
						
			if(!configDir.exists()){
				configDir.mkdir();
				Files.walk(defaultConfigDir.toPath()).forEach(path -> 
				{
					try {
						Files.copy(path, configDir.toPath().resolve(path.getFileName()));
					} catch (IOException ioException) {
						LOG.error("Fail to copy "+path+" to " + configurationPath);
					}
				});
			} 
			
			XMLConfig.setBaseConfigPath(configurationPath);
			XMLConfig.reloadAll();
		
		} catch (Exception exception) {
			LOG.error("Fail to set configuration path " + configurationPath);
		}
	}
	
	private void notifyConnection() throws JMSException {	
		Queue notificationQueue = session.createQueue("instance/connection"); 
		MessageProducer notificationProducer = session.createProducer(notificationQueue);									
		TextMessage notificationMessage = session.createTextMessage(String.format("{\"token\":\"%s\"}", busId));
		notificationProducer.send(notificationMessage);
		notificationProducer.close();
		
	}	
	
	@Override
	public void onException(JMSException exception) {
		LOG.error("Scheduling reconnection");
		timer.schedule(new ReconnectionTask(), 60000);
		
		
	}
	
	class ReconnectionTask extends TimerTask {
		@Override
		public void run() {
			init();
		}		
	}	
	
}