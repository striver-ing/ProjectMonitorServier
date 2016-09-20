package com.server;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.activation.CommandMap;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import com.db.operation.CRUD;
import com.pojo.CommandMsg;
import com.utils.Constance;
import com.utils.Log;
import com.utils.Tools;

/**
 * @author Boris
 * @description 
 * 2016��9��18��
 */
public class Server implements Runnable{
	private Socket socket = null;
	private InputStream is = null;
	private OutputStream os= null;
	private Tools tools;
	private CRUD crud;
	
	private int searchCommandTime;
	private int clientPort;
	private String clientIP;
	
	private static final String TB_CLIENT_MSG = "client_msg";
	private static final String TB_CPU_MSG = "cpu_msg";
	private static final String TB_PHYSICAL_MEMORY_MSG = "physical_memory_msg";
	private static final String TB_PROJECT_MSG = "project_msg";
	private static final String TB_SERVER_MSG = "server_msg";
	private static final String TB_THREAD_MSG = "thread_msg";
	
	public static final String LISTEN_CLIENT = "0";
	public static final String SEARCH_COMMAND = "1";
	
	public Server(Socket socket){
		tools = Tools.getTools();
		crud = new CRUD();
		searchCommandTime =Integer.parseInt(tools.getProperty("search_command_time"));
		
		try {
			this.socket = socket;
			is = socket.getInputStream();
			os = socket.getOutputStream();
			clientIP = socket.getInetAddress().toString().substring(1);
			clientPort = socket.getPort();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		CommandMsg command = new CommandMsg();
		command.setClientPort(clientPort);
		command.setSerIp(clientIP);
		command.setCommand("PROcess:START");
		command.setStatus(Constance.CommandStatus.TODO);
		//test
		JSONObject json = JSONObject.fromObject(command);
		System.out.println(json);
		addMsgToDB(json, "command_msg", true);
		
	}
	
	private void listenerClient(){
		boolean flag = true;
		while(flag){
			byte[] buffer = new byte[4096];
			try {
				int length =  is.read(buffer);
				String msg = new String(buffer, 0, length);
				System.out.println(length);
				Log.out.debug("rec - " + msg);
				
				dealRecMsg(msg);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
		
	private void dealRecMsg(String msg){
        JSONObject json = JSONObject.fromObject(msg);
		
//        JSONObject jsonBean = json.getJSONObject("clientMsg");
//        Object logpath = jsonBean.get("cliLogPath");
//        System.out.println(logpath);
//        System.out.println(tools.formateSqlValue(logpath));
//        
        JSONObject jsonBean = null;
        int serverId;
		int projectId;
		
		jsonBean = json.getJSONObject("serverMsg");
		addMsgToDB(jsonBean, TB_SERVER_MSG);
		serverId = getIdInDB(jsonBean, TB_SERVER_MSG);
		
		jsonBean = json.getJSONObject("projectMsg");
		jsonBean.put("forSerId", serverId);
		addMsgToDB(jsonBean, TB_PROJECT_MSG);
		projectId = getIdInDB(jsonBean, TB_PROJECT_MSG);
		
		jsonBean = json.getJSONObject("clientMsg");
		jsonBean.put("forProId", projectId);
		jsonBean.put("forSerId", serverId);	
		jsonBean.put("cliPort", clientPort);
		addMsgToDB(jsonBean, TB_CLIENT_MSG, true);
		
		jsonBean = json.getJSONObject("threadMsg");
		jsonBean.put("forProId", projectId);
		jsonBean.put("forSerId", serverId);	
		addMsgToDB(jsonBean, TB_THREAD_MSG, true);
		
		JSONArray jsonArray;
		
		jsonArray = json.getJSONArray("cpuMsgs");
		for (int i = 0; i < jsonArray.size(); i++) {
			jsonBean = jsonArray.getJSONObject(i);
			jsonBean.put("forSerId", serverId);
			addMsgToDB(jsonBean, TB_CPU_MSG);
		}
		
		jsonArray = json.getJSONArray("physicalMemoryMsgs");
		for (int i = 0; i < jsonArray.size(); i++) {
			jsonBean = jsonArray.getJSONObject(i);
			jsonBean.put("forSerId", serverId);
			addMsgToDB(jsonBean, TB_PHYSICAL_MEMORY_MSG);
		}
	}
	
	/**
	 * @Method: addMsgToDB 
	 * @Description: �����ݿ�������Ϣ
	 * @param json ������Ϣ��json
	 * @param table ���ݿ��
	 * @param checkRepeat �Ƿ񸲸��ظ�������
	 * void
	 */
	private void addMsgToDB(JSONObject json, String table){
		addMsgToDB(json, table, false);
	}
	private void addMsgToDB(JSONObject json, String table, boolean checkRepeat){
		if (checkRepeat && getIdInDB(json, table) != 0) {
			updateMsgInDB(json, table);
		}else{
			insertMsgToDB(json, table);			
		}
	}
	
	private int getIdInDB(JSONObject json, String table){
		int id = 0;
		String primaryKeyName = null;
		String sql = "";
		String condition = "";
		
		JSONArray fieldArray = json.getJSONArray("field");
		//content
		for (int i = 0; i < fieldArray.size(); i++) {
			String key = fieldArray.getString(i);
			if (key.startsWith("pk")) {
				primaryKeyName = tools.camelToUnderline(key);
				continue;
			}else if(key.startsWith("for")){
				continue;
			}
			Object value = json.get(key);
			value = tools.formateSqlValue(value);
			key = tools.camelToUnderline(key);
			
			condition += key + "=" + value + " and ";
		}
		condition = condition.substring(0, condition.length() - 4);
		sql = "select " + primaryKeyName + " from " + table + " where " + condition;
//		System.out.println(sql);
		
		ResultSet result = crud.find(sql);
	
		try {
			while (result.next()) {
				id = result.getInt(1);
				break;
			}
			
			result.close();
			crud.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		return id;
	}
	
	private void updateMsgInDB(JSONObject json, String table){
		String primaryKeyName = null;
		String sql = "update " + table + " set ";
		JSONArray fieldArray = json.getJSONArray("field");
		
		//content
		for (int i = 0; i < fieldArray.size(); i++) {
			String key = fieldArray.getString(i);
			if (key.startsWith("pk")) {
				primaryKeyName = tools.camelToUnderline(key);
				continue;
			}
			Object value = json.get(key);
			value = tools.formateSqlValue(value);
			key = tools.camelToUnderline(key);
			
			sql += key + "=" + value + ",";
		}
		sql += "record_time = CURRENT_TIMESTAMP";
		sql += " where " + primaryKeyName + "=" + getIdInDB(json, table);
//		System.out.println(sql);
		
		crud.update(sql);
		
	}
	
	private void insertMsgToDB(JSONObject json, String table){
		String sql = "insert into " + table + " (";
		JSONArray fieldArray = json.getJSONArray("field");
		//key
		for (int i = 0; i < fieldArray.size(); i++) {
			String key = fieldArray.getString(i);
			key = tools.camelToUnderline(key);
			sql += key + ",";
		}
		sql = sql.substring(0, sql.length()- 1);
		sql += ") values (";
		
		//value
		for (int i = 0; i < fieldArray.size(); i++) {
			String key = fieldArray.getString(i);
			Object value = json.get(key);
			value = tools.formateSqlValue(value);
			sql += value + ",";
		}
		sql = sql.substring(0, sql.length()- 1);
		sql += ")";
		
//		System.out.println(sql);
		
		crud.instert(sql);
	}
	
	private void searchCommand(){
		while(true){
			try {
				String sql = "select command from command_msg where ser_ip='" + clientIP + "' and client_port=" + clientPort + " and status=" + Constance.CommandStatus.TODO + " ORDER BY record_time";
				ResultSet resultSet = crud.find(sql);
				while(resultSet.next()){
					String command = resultSet.getString(1);
					Log.out.debug("send - " + command);
					sendMsgToClient(command);
					break;
				}
				resultSet.close();
				crud.close();
				
				Thread.sleep(searchCommandTime);
				break;
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	private void sendMsgToClient(String msg){
		try {
			os.write(msg.getBytes());
			String sql = "update command_msg set status=" + Constance.CommandStatus.DOING + " where ser_ip='" + clientIP + "' and client_port=" + clientPort;
			crud.update(sql);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		if (Thread.currentThread().getName().equals(LISTEN_CLIENT)) {
			listenerClient();
		}else if (Thread.currentThread().getName().equals(SEARCH_COMMAND)){
			searchCommand();
		}
	}

}