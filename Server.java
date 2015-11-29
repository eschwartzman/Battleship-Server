/**
 * authors: Evan Schwartzman
 */
package battleship.client.mains;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import battleship.client.util.SingleServerQueue;
import battleship.client.util.Wrappers;
import battleship.game.Board;
import battleship.game.Coordinate;
import battleship.game.HitResult;
import battleship.game.Ship;

public class Server {
	final private int port;
	DataInputStream[]  is = new DataInputStream[2];
	DataOutputStream[] os = new DataOutputStream[2];
	private boolean player2Ready = false;
	boolean playing = false;
	String playerName[] = new String[2];
	boolean won = false;
	Board boards[] = new Board[2];
	Coordinate playerMove[] = new Coordinate[2];
	Ship[][] shipArray;
	int curPlayer;
	public Server(int port) {
		this.port = port;	
	}
	public void run() throws IOException {
		ServerSocket iserver = new ServerSocket(port);
		//Two players
		for(int i = 0; i< 2; i++){
			final int player = i;
			final Socket h = iserver.accept();
			Thread t = new Thread(){
				public void run(){
					try{
						DataOutputStream outStream = new DataOutputStream(h.getOutputStream());
						DataInputStream inStream = new DataInputStream(h.getInputStream());
						shipArray = new Ship[2] [5];
						is[player] = inStream;
						os[player] = outStream;

						playerName[player] = is[player].readUTF();
						String password = is[player].readUTF();
						int role = is[player].readByte();

						//Server name
						os[player].writeUTF("Welcome to Battleship!");
						//this line writes the player ID
						os[player].writeByte(player +1);
						//Now, a board is created for each player
						Board b = new Board(18, 18);
						boards[player] = b;

						//configuration
						os[player].writeUTF("config");
						os[player].writeShort(boards[player].getNumRows());
						os[player].writeShort(boards[player].getNumCols());
						//Ship creation
						os[player].writeByte(5);
						int[] sizes = new int[] { 1, 2, 3, 4, 5 };
						for (int size : sizes) {
							os[player].writeByte(size);
						}
						//Place created ships
						for (int i = 0;i<5;i++) {
							int sOrientation = is[player].readByte();
							int sRow = is[player].readShort();
							int sColumn = is[player].readShort();
							Coordinate c = new Coordinate(sRow, sColumn);
							if(sOrientation == 0){
								shipArray[player][i] = Ship.genHorizontalShip(c, sizes[i]);  
								boards[player].placeShip(shipArray[player] [i]);
							}else if (sOrientation == 1) {
								shipArray[player][i] = Ship.genVerticalShip(c, sizes[i]);
								boards[player].placeShip(shipArray[player] [i]);
							}
						}
						os[player].writeUTF("ok");
						curPlayer = 0;   // will switch from 0 to 1 throughout --> P1 and p2
						new  Thread(){
							public synchronized void run(){
								try {
									while(true){
										String message = is[player].readUTF();
										if(message.equals("move")){
												int rFire = is[curPlayer].readShort();
												int cFire = is[curPlayer].readShort();
												Coordinate c = new Coordinate(rFire, cFire);
												playerMove[curPlayer] = c;
												alertMove(os, curPlayer);
												logFire(os, curPlayer, c);
												Wrappers.notifyAll(Server.this);
											
										}else if (message.equals("message")) {
												sendMessage(os, is, player);
										}
										else throw new Error("Bad message from cleint " + message);
									}
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}.start();

						if (player == 0) {
							// wait for player 2
							while (!player2Ready) {
								Wrappers.wait(Server.this);
							}
							playing = true;
							while (playing) {
								playerMove[curPlayer] = null;
								if(won == false){
									turn(os, curPlayer);
								}
								while(playerMove[curPlayer] == null) {
									Wrappers.wait(Server.this);
								}	
								if(curPlayer == 0){
									curPlayer = 1;
								}else{
									curPlayer = 0;
								}	
							}
						}else {
							player2Ready = true;
							Wrappers.notifyAll(Server.this);
						}
					}catch(Throwable t){
						t.printStackTrace();
						throw new Error("Saw Error" + t);
					}
				}
			};
			t.start();
		}
	}
	public static void main(String[] args) throws IOException {
		Server server = new Server(3001);
		server.run();
	}
	public void turn(DataOutputStream[] o, int player ) throws IOException{
		o[0].writeUTF("PlayerTurn");
		o[0].writeByte(player+1);
		o[1].writeUTF("PlayerTurn");
		o[1].writeByte(player+1);
	}
	public void alertMove(DataOutputStream[] o, int player ) throws IOException{
		o[0].writeUTF("PlayerFires");
		o[0].writeByte(player+1);
		o[1].writeUTF("PlayerFires");
		o[1].writeByte(player+1);
	}
	public void sendMessage(DataOutputStream[] o, DataInputStream[] i, int player ) throws IOException{
		String contents = i[player].readUTF();
		o[0].writeUTF("broadcast");
		o[0].writeUTF(playerName[player]);
		o[0].writeUTF(contents);
		o[1].writeUTF("broadcast");
		o[1].writeUTF(playerName[player]);
		o[1].writeUTF(contents);
		if(contents.equals("\nYou win!\n")){
			won = true;
		}
	}
	public void logFire(DataOutputStream[] o, int p, Coordinate c ) throws IOException{
		int tempPlayer = p;
		o[0].writeShort(c.row);
		o[0].writeShort(c.col);
		o[1].writeShort(c.row);
		o[1].writeShort(c.col);
		if (tempPlayer == 0){
			tempPlayer = 1;
		}else if (tempPlayer == 1){
			tempPlayer = 0;
		}
		HitResult h = new HitResult(boards[tempPlayer].getPCS());
		boards[tempPlayer].processHit(c);
		o[0].writeUTF(h.isHit());
		o[0].writeUTF(h.isSunk());
		o[0].writeUTF(h.isWin());
		o[1].writeUTF(h.isHit());
		o[1].writeUTF(h.isSunk());
		o[1].writeUTF(h.isWin());
	}
}
