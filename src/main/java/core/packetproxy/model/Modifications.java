/*
 * Copyright 2019 DeNA Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package packetproxy.model;

import com.j256.ormlite.dao.Dao;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.swing.JOptionPane;
import packetproxy.model.Database.DatabaseMessage;

public class Modifications extends Observable implements Observer
{
	private static Modifications incetance;
	
	public static Modifications getInstance() throws Exception {
		if (incetance == null) {
			incetance = new Modifications();
		}
		return incetance;
	}
	
	private Database database;
	private Dao<Modification,Integer> dao;
	private Servers servers;
	
	private Modifications() throws Exception {
		database = Database.getInstance();
		servers = Servers.getInstance();
		dao = database.createTable(Modification.class, this);
		if (!isLatestVersion()) {
			RecreateTable();
		}
	}
	public void create(Modification modification) throws Exception {
		modification.setEnabled();
		dao.createIfNotExists(modification);
		notifyObservers();
	}
	public void delete(int id) throws Exception {
		dao.deleteById(id);
		notifyObservers();
	}
	public void delete(Modification modification) throws Exception {
		dao.delete(modification);
		notifyObservers();
	}
	public void update(Modification modification) throws Exception {
		dao.update(modification);
		notifyObservers();
	}
	public void refresh() {
		notifyObservers();
	}
	public Modification query(int id) throws Exception {
		return dao.queryForId(id);
	}
	public List<Modification> queryAll() throws Exception {
		return dao.queryBuilder().query();
	}

	public List<Modification> queryEnabled(Server server) throws Exception {
		int server_id = Modification.ALL_SERVER;
		if (server != null) { server_id = server.getId(); }
		return dao.queryBuilder().where()
				.eq("server_id",  server_id)
				.or()
				.eq("server_id",  Modification.ALL_SERVER)
				.and()
				.eq("enabled", true)
				.query();
	}
	public byte[] replaceOnRequest(byte[] data, Server server, Packet client_packet) throws Exception {
		for (Modification mod : queryEnabled(server)) {
			if (mod.getDirection() == Modification.Direction.CLIENT_REQUEST || mod.getDirection() == Modification.Direction.ALL)
				data = mod.replace(data, client_packet);
		}
		return data;
	}
	public byte[] replaceOnResponse(byte[] data, Server server, Packet server_packet) throws Exception {
		for (Modification mod : queryEnabled(server)) {
			if (mod.getDirection() == Modification.Direction.SERVER_RESPONSE || mod.getDirection() == Modification.Direction.ALL)
				data = mod.replace(data, server_packet);
		}
		return data;
	}
	@Override
	public void notifyObservers(Object arg) {
		setChanged();
		super.notifyObservers(arg);
		clearChanged();
	}
	@Override
	public void addObserver(Observer observer) {
		super.addObserver(observer);
		servers.addObserver(observer);
	}
	@Override
	public void update(Observable o, Object arg) {
		DatabaseMessage message = (DatabaseMessage)arg;
		try {
			switch (message) {
			case PAUSE:
				// TODO ロックを取る
				break;
			case RESUME:
				// TODO ロックを解除
				break;
			case DISCONNECT_NOW:
				break;
			case RECONNECT:
				database = Database.getInstance();
				dao = database.createTable(Modification.class, this);
				notifyObservers(arg);
				break;
			case RECREATE:
				database = Database.getInstance();
				dao = database.createTable(Modification.class, this);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private boolean isLatestVersion() throws Exception {
		String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='modifications'").getFirstResult()[0];
//		System.out.println(result);
		return result.equals("CREATE TABLE `modifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `enabled` BOOLEAN , `server_id` INTEGER , `direction` VARCHAR , `pattern` VARCHAR , `method` VARCHAR , `replaced` VARCHAR , UNIQUE (`server_id`,`direction`,`pattern`,`method`) )");
	}
	private void RecreateTable() throws Exception {
		int option = JOptionPane.showConfirmDialog(null,
				"Modificationsテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
				"テーブルの更新",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {
			database.dropTable(Modification.class);
			dao = database.createTable(Modification.class, this);
		}
	}
}
