// 必要なライブラリのインポート
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import javax.swing.*;

public class Labyrinth extends JFrame implements MouseListener,MouseMotionListener {
    // ゲームの基本要素を格納するフィールド変数
    private JButton room[][];           // 部屋を表すボタンの2次元配列
    private JButton wallvertical[][];   // 縦の壁を表すボタンの2次元配列
    private JButton wallhorizontal[][]; // 横の壁を表すボタンの2次元配列
    JLabel theLabelA;                   // ラベル用変数
    private Container c;                // コンテナ用変数
    PrintWriter out;                    // サーバーへの出力用ライター
    
    // ゲームの状態を管理するカウンター
    private int entranceCount = 0;      // 入口の設置回数をカウント
    private int exitCount = 0;          // 出口の設置回数をカウント
    private int wallCount = 0;          // 設置された壁の数をカウント
    
    // ゲームの準備状態を管理するフラグ
    private boolean isReady = false;        // 自分の準備状態
    private boolean opponentReady = false;  // 相手の準備状態
    
    // ゲームで使用する画像アイコン
    private ImageIcon background, entrance, exit;           // 背景、入口、出口のアイコン
    private ImageIcon right, left, up, down;               // 方向を示すアイコン
    private ImageIcon walloff, wallon;                     // 壁の状態を示すアイコン
    
    // 現在の入口と出口の位置を保持
    private JButton currentEntrance = null;  // 現在の入口ボタン
    private JButton currentExit = null;      // 現在の出口ボタン
    
    // 迷路の構造を管理するデータ構造
    private int[][] grid;                    // 迷路の状態を保持する2次元配列
    private ArrayList<int[]> walls;          // 壁の位置情報を保持するリスト
    private int[] startPosition;             // 入口の位置座標
    private int[] endPosition;               // 出口の位置座標
    
    // ゲーム進行に関する要素
    private GameScreen gameScreen;           // ゲーム画面の管理用オブジェクト
    private JButton readyButton;             // 準備完了ボタン
    private String myName;                   // プレイヤーの名前
    private boolean isFirstPlayer = false;   // 先行プレイヤーかどうか
    private boolean isMyTurn = false;        // 自分のターンかどうか
    
    // 背景画像の管理
    private ImageIcon firstBackgroundImage;   // 最初の画面の背景画像
    private ImageIcon secondBackgroundImage;  // 対戦画面の背景画像


    public Labyrinth() {
        // グリッドの初期化（11x11）
        grid = new int[11][11];
        walls = new ArrayList<>();

        //名前の入力ダイアログを開く
        this.myName = JOptionPane.showInputDialog(null,"名前を入力してください","名前の入力",JOptionPane.QUESTION_MESSAGE);
        String ipadress = JOptionPane.showInputDialog(null,"IPアドレスを入力してください","IPアドレスの入力",JOptionPane.QUESTION_MESSAGE);
        if(this.myName.equals("")){
            this.myName = "No name";//名前がないときは，"No name"とする
        }
        if(ipadress.equals("")){
            ipadress = "localhost";//IPアドレスが入力されない時"Localhost"に接続
        }

        //ウィンドウを作成する
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("Labyrinth");
        setSize(2000,1200);
        setLayout(null);

        // 背景画像の読み込み
        firstBackgroundImage = new ImageIcon(new File("fastbackground.png").getAbsolutePath());
        secondBackgroundImage = new ImageIcon(new File("secondbackground.png").getAbsolutePath());

        // 背景パネルの作成と設定
        JPanel backgroundPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // 背景画像を描画
                g.drawImage(firstBackgroundImage.getImage(), 0, 0, getWidth(), getHeight(), this);
            }
        };
        backgroundPanel.setLayout(null);
        backgroundPanel.setBounds(0, 0, 2000, 1200);
        backgroundPanel.setOpaque(false);

        // 既存のコンポーネントを背景パネルに追加
        c = backgroundPanel;

        // 背景パネルをフレームに追加
        getContentPane().add(backgroundPanel);

        //アイコンの設定
        background = new ImageIcon(new File("background.png").getAbsolutePath());
        entrance = new ImageIcon(new File("in.png").getAbsolutePath());
        exit = new ImageIcon(new File("out.png").getAbsolutePath());
        walloff = new ImageIcon(new File("walloff.png").getAbsolutePath());
        wallon = new ImageIcon(new File("wallon.png").getAbsolutePath());
        
        // グリッドの初期化
        for(int i = 0; i < 11; i++) {
            for(int j = 0; j < 11; j++) {
				if (i%2!=0 && j%2!=0) {
					grid[i][j] = 1; // 空白の場所は壁としてあつかう
				} else{
					grid[i][j] = 0;
				}
            }
        }
		

        c.setLayout(null);
        setupMazeButtons();
        setupWalls();
        setupRightPanel();
        setupReadyButton();
        setupServerConnection(myName, ipadress);
    }

    // 迷路のボタン設定
    private void setupMazeButtons() {
        room = new JButton[6][6];
        for(int j=0;j<6;j++){
            for(int i=0;i<6;i++){
                room[j][i] = new JButton(background);
                c.add(room[j][i]);
                room[j][i].setBounds(i*110+100,j*110+100,100,100);
                room[j][i].addMouseListener(this);
                room[j][i].setActionCommand(Integer.toString(j*6+i));
            }
        }
		
		//迷宮の番号（A-F,1-6）の文字を描画
		for(int i=0;i<6;i++){//1-6の文字を描画
			JLabel theLabelA = new JLabel(String.valueOf(i + 1)); // 数字をStringに変換
			theLabelA.setFont(new Font("Arial", Font.BOLD, 30)); // フォントサイズを30に設定
            theLabelA.setBounds(i * 110 + 145, 50, 100, 30); // 各ラベルを横に配置
            c.add(theLabelA); // コンテナにラベルを追加
		}
		for(int j=0;j<6;j++){//A-Fの文字を描画
			JLabel theLabelA = new JLabel(String.valueOf((char) ('A' + j))); // 'A'から順に文字を生成
			theLabelA.setFont(new Font("Arial", Font.BOLD, 30)); // フォントサイズを30に設定
            theLabelA.setBounds(50, j * 110 + 140, 100, 30); // 各ラベルを横に配置
            c.add(theLabelA); // コンテナにラベルを追加
		}
    }

    // 壁ボタンの設定
    private void setupWalls() {
        wallvertical = new JButton[6][5];//垂直の壁（縦）
        for(int j=0;j<6;j++){
            for(int i=0;i<5;i++){
                wallvertical[j][i] = new JButton(walloff);
                c.add(wallvertical[j][i]);
                wallvertical[j][i].setBounds(i*110+200,j*110+100,10,100);
                wallvertical[j][i].addMouseListener(this);
                wallvertical[j][i].setActionCommand(Integer.toString(j*5+i+100));
                wallvertical[j][i].setOpaque(true);
                wallvertical[j][i].setContentAreaFilled(true);
                wallvertical[j][i].setBorderPainted(true);
            }
        }

        wallhorizontal = new JButton[5][6];//水平の壁（横）
        for(int j=0;j<5;j++){
            for(int i=0;i<6;i++){
                wallhorizontal[j][i] = new JButton(walloff);
                c.add(wallhorizontal[j][i]);
                wallhorizontal[j][i].setBounds(i*110+100,j*110+200,100,10);
                wallhorizontal[j][i].addMouseListener(this);
                wallhorizontal[j][i].setActionCommand(Integer.toString(j*6+i+200));
                wallhorizontal[j][i].setOpaque(true);
                wallhorizontal[j][i].setContentAreaFilled(true);
                wallhorizontal[j][i].setBorderPainted(true);
            }
        }
    }
	
	

    // 右パネルの設定
    private void setupRightPanel() {
        // 1. 入口と出口の見出し
        theLabelA = new JLabel("1.入口と出口を設定");
        theLabelA.setFont(new Font("Meiryo", Font.BOLD, 50));
        theLabelA.setBounds(960, 100, 500, 80);
        c.add(theLabelA);

        // 説明文
        theLabelA = new JLabel("<html>迷宮のマスをクリックすると，入口と出口を設定できます．<br>また，交互に置き換えることができます．</html>");
        theLabelA.setFont(new Font("Yu Mincho Regular", Font.BOLD, 30));
        theLabelA.setBounds(990, 170, 900, 130);
        c.add(theLabelA);

        // 2. 壁の設定
        theLabelA = new JLabel("2.壁の設定");
        theLabelA.setFont(new Font("Meiryo", Font.BOLD, 50));    
        theLabelA.setBounds(960, 320, 500, 80);
        c.add(theLabelA);

        // 説明文
        theLabelA = new JLabel("<html>迷宮の壁をクリックすると，壁を設定できます．<br>20枚まで設置可能ですが，攻略できるよう配置してください．</html>");
        theLabelA.setFont(new Font("Yu Mincho Regular", Font.BOLD, 30));
        theLabelA.setBounds(990, 390, 900, 130);
        c.add(theLabelA);

        // 3. 準備完了
        theLabelA = new JLabel("3.準備完了");
        theLabelA.setFont(new Font("Meiryo", Font.BOLD, 50));
        theLabelA.setBounds(960, 550, 500, 80);
        c.add(theLabelA);

        // 説明文
        theLabelA = new JLabel("<html>準備が完了したら，準備完了を押してください．<br>相手の準備が完了すると，ゲームが始まります．<br><font color='red'>なお，先に準備ができたプレイヤーに先行の権利が与えられます．</font></html>");
        theLabelA.setFont(new Font("Yu Mincho Regular", Font.BOLD, 30));
        theLabelA.setBounds(990, 620, 1100, 150);
        c.add(theLabelA);
    }

    // 準備完了ボタンの設定
    private void setupReadyButton() {
        readyButton = new JButton("準備完了");
        readyButton.setFont(new Font("Meiryo", Font.BOLD, 30));
        readyButton.setBounds(1100, 800, 200, 100);
        readyButton.setEnabled(false); // 初期状態では無効化，入口と出口を設定すると押せるように
        c.add(readyButton);
        readyButton.addMouseListener(this);
        readyButton.setActionCommand("ready");//ボタンが押されたらreadyコマンドを送信
    }

    // サーバー接続の設定
    private void setupServerConnection(String myName, String ipadress) {
        Socket socket = null;
        try {
            socket = new Socket(ipadress, 10000);
            MesgRecvThread mrt = new MesgRecvThread(socket, myName);
            mrt.start();
        } catch (UnknownHostException e) {
            System.err.println("ホストの IP アドレスが判定できません: " + e);
        } catch (IOException e) {
            System.err.println("エラーが発生しました: " + e);
        }
    }

    // メインメソッド
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                Labyrinth net = new Labyrinth();
                net.setVisible(true);
            } catch (Exception e) {
                System.err.println("エラーが発生しました: " + e);
                e.printStackTrace();
            }
        });
    }

    // ゲーム画面への切り替えメソッド
	private synchronized void switchToGameScreen() {
		// ゲーム画面への切り替えを行う
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					// 現在のコンテナを取得
					Container contentPane = getContentPane();
					
					// 画面上の全てのコンポーネントを削除
					contentPane.removeAll();
					
					// ゲーム画面がまだ生成されていない場合、新しく生成
					if (gameScreen == null) {
						gameScreen = new GameScreen(room, out, myName);
						
						// ゲーム画面の位置とサイズを設定
						gameScreen.setBounds(0, 0, 2000, 1200);
					}
					
					// ゲーム画面をコンテナに追加
					contentPane.add(gameScreen);
					
					// レイアウトを再計算
					validate();
					
					// 再描画を行う
					repaint();
					
					// デバッグ用のメッセージを表示
					//System.out.println("ゲーム画面に移行成功");
				} catch (Exception e) {
					// エラーが発生した場合の処理
					System.out.println("次の理由で画面以降できませんでした: " + e.getMessage());
					e.printStackTrace();
				}
			}
		});
	}

    // MouseListenerの実装（調べただけだから動作不安ポイント）
    public void mouseClicked(MouseEvent e) {
        JButton theButton = (JButton)e.getComponent();//コンポーネント取得
        String theArrayIndex = theButton.getActionCommand();//コマンド取得
        Icon theIcon = theButton.getIcon();//アイコン取得
        
        if (isRoomButton(theButton)) {//roomボタンが押されたとき
            handleRoomButtonClick(theButton, theIcon);//入口または出口を設定
        } else if (isWallButton(theButton)) {//wallボタンが押されたとき
            handleWallButtonClick(theButton, theArrayIndex);
        } else if (theButton.getActionCommand().equals("ready")) {//準備完了ボタンが押されたとき
            handleReadyButtonClick();
        }
    }
	
	//部屋ボタンが押されたときの処理
    private void handleRoomButtonClick(JButton theButton, Icon theIcon) {
        if (theIcon == background) {
            // 配置先の座標を計算
            int buttonIndex = Integer.parseInt(theButton.getActionCommand());
            int roomX = buttonIndex % 6;
            int roomY = buttonIndex / 6;
            int gridX = roomX * 2;
            int gridY = roomY * 2;
            
            // 完全に壁で囲まれているかチェック（常に行う）
            if (isCompletelyWalled(gridX, gridY)) {
                JOptionPane.showMessageDialog(null, "四方を壁で完全に囲まれた場所には設置できません", "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (entranceCount == 0) {//エントランスカウント０（次は入口を置く） 
                if (currentEntrance != null && currentExit != null) {//入口と出口が設定済みの場合
                    // 元のグリッド状態を保存
                    int[][] tempGrid = copyGrid(grid);
                    
                    // 元の入口と出口の位置を取得
                    int startX = getGridX(currentEntrance);
                    int startY = getGridY(currentEntrance);
                    int endX = getGridX(currentExit);
                    int endY = getGridY(currentExit);
                    
                    // 新しい入口の位置を仮に設定
                    tempGrid[gridY][gridX] = 2; // 新しい入口
                    tempGrid[startY][startX] = 0; // 元の入口をリセット
                    
                    // 新しい入口から出口への経路が存在するか確認
                    if (isPathExistOnGrid(tempGrid, gridX, gridY, endX, endY)) {
                        // 経路が存在する場合、実際にグリッドを更新
                        currentEntrance.setIcon(background);
                        resetGridPosition(currentEntrance);
                        theButton.setIcon(entrance);
                        currentEntrance = theButton;
                        updateGridPosition(theButton, 2); // grid 2 が入口をあらわす
                        setStartPosition(theButton);
                        entranceCount = 1 - entranceCount;
                    } else {
                        JOptionPane.showMessageDialog(null, "そこには入口を置けません", "エラー", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    // 初めて入口を設置する場合
                    theButton.setIcon(entrance);
                    currentEntrance = theButton;
                    updateGridPosition(theButton, 2); // grid 2 が入口をあらわす
                    setStartPosition(theButton);
                    entranceCount = 1 - entranceCount;
                }
            } else {//出口の設定
                if (currentExit != null && currentEntrance != null) {//出口が設定されている場合は，出口リセット
                    // 元のグリッド状態を保存
                    int[][] tempGrid = copyGrid(grid);
                    
                    // 元の入口と出口の位置を取得
                    int startX = getGridX(currentEntrance);
                    int startY = getGridY(currentEntrance);
                    int endX = getGridX(currentExit);
                    int endY = getGridY(currentExit);
                    
                    // 新しい出口の位置を仮に設定
                    tempGrid[gridY][gridX] = 3; // 新しい出口
                    tempGrid[endY][endX] = 0; // 元の出口をリセット
                    
                    // 入口から新しい出口への経路が存在するか確認
                    if (isPathExistOnGrid(tempGrid, startX, startY, gridX, gridY)) {
                        // 経路が存在する場合、実際にグリッドを更新
                        currentExit.setIcon(background);
                        resetGridPosition(currentExit);
                        theButton.setIcon(exit);
                        currentExit = theButton;
                        updateGridPosition(theButton, 3); // grid 3 が出口をあらわす
                        setEndPosition(theButton);
                        entranceCount = 1 - entranceCount;
                    } else {
                        JOptionPane.showMessageDialog(null, "そこには出口を置けません", "エラー", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    // 初めて出口を設置する場合
                    theButton.setIcon(exit);
                    currentExit = theButton;
                    updateGridPosition(theButton, 3); // grid 3 が出口をあらわす
                    setEndPosition(theButton);
                    entranceCount = 1 - entranceCount;
                }
            }
        } else if (theIcon == entrance || theIcon == exit) {
            JOptionPane.showMessageDialog(null, "重ねて配置はできません", "エラー", JOptionPane.ERROR_MESSAGE);
        }
        updateReadyButtonState();//準備完了ボタンが押せるのかどうかを更新
    }

    // グリッドをコピーするヘルパーメソッド
    private int[][] copyGrid(int[][] original) {
        int[][] copy = new int[original.length][];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i].clone();
        }
        return copy;
    }

    // ボタンからグリッドのX座標を取得するヘルパーメソッド
    private int getGridX(JButton button) {
        int buttonIndex = Integer.parseInt(button.getActionCommand());
        int roomX = buttonIndex % 6;
        return roomX * 2;
    }

    // ボタンからグリッドのY座標を取得するヘルパーメソッド
    private int getGridY(JButton button) {
        int buttonIndex = Integer.parseInt(button.getActionCommand());
        int roomY = buttonIndex / 6;
        return roomY * 2;
    }

    // 特定のグリッド座標が四方を壁に囲まれているかチェックするメソッド（完全に囲まれた場合のみtrue）
    private boolean isCompletelyWalled(int gridX, int gridY) {
        // 上下左右4方向すべてに壁があるかチェック
        boolean topWall = gridY > 0 && grid[gridY-1][gridX] == 1;
        boolean bottomWall = gridY < 10 && grid[gridY+1][gridX] == 1;
        boolean leftWall = gridX > 0 && grid[gridY][gridX-1] == 1;
        boolean rightWall = gridX < 10 && grid[gridY][gridX+1] == 1;
        
        // 4方向すべてに壁がある場合のみtrue
        return topWall && bottomWall && leftWall && rightWall;
    }

    // 指定されたグリッド上で経路が存在するか確認するメソッド
    private boolean isPathExistOnGrid(int[][] checkGrid, int startX, int startY, int endX, int endY) {
        boolean[][] visited = new boolean[11][11];
        Queue<int[]> queue = new LinkedList<>();
        
        queue.offer(new int[]{startX, startY});
        visited[startY][startX] = true; // 注意: Y, Xの順番で配列インデックスを使用
        
        int[] dx = {0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0};
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0];
            int y = current[1];
            
            if (x == endX && y == endY) {
                return true; // 目的地に到達
            }
            
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                
                if (isInsideGrid(nx, ny) && !visited[ny][nx]) {
                    if (checkGrid[ny][nx] == 0 || checkGrid[ny][nx] == 3) {
                        queue.offer(new int[]{nx, ny});
                        visited[ny][nx] = true;
                    }
                }
            }
        }
        
        return false; // 経路が見つからない
    }

    // グリッド内の座標かどうかを確認するメソッド（メソッド名をより明確に）
    private boolean isInsideGrid(int x, int y) {
        return x >= 0 && x < grid[0].length && y >= 0 && y < grid.length;
    }


	//壁のボタンが押されたときの判定
    private void handleWallButtonClick(JButton theButton, String theArrayIndex) {//wallボタンクリック時，ボタンの座標をtoggleWallに格納
        if (currentEntrance != null && currentExit != null) {//入口と出口が設定されているときに壁を置ける
            if (Integer.parseInt(theArrayIndex) >= 200) {//横の壁かどうかを判定
                int row = (Integer.parseInt(theArrayIndex)-200) / 6;
                int col = (Integer.parseInt(theArrayIndex)-200) % 6;
                toggleWall(theButton, row, col, false);//toggleWallをfalseに
            } else {//縦の壁であるとき
                int row = (Integer.parseInt(theArrayIndex)-100) / 5;
                int col = (Integer.parseInt(theArrayIndex)-100) % 5;
                toggleWall(theButton, row, col, true);//toggleWallをtrueに
            }
			//スタートとゴールの座標も同時に計算（どこかに統合できないかな？）
            String currentEntranceArrayIndexStr = currentEntrance.getActionCommand();
            int currentEntranceArrayIndex = Integer.parseInt(currentEntranceArrayIndexStr);
            int startX = (currentEntranceArrayIndex % 6);
            int startY = (currentEntranceArrayIndex / 6);
            String currentExitArrayIndexStr = currentExit.getActionCommand();
            int currentExitArrayIndex = Integer.parseInt(currentExitArrayIndexStr);
            int endX = (currentExitArrayIndex % 6);
            int endY = (currentExitArrayIndex / 6);
			/*
            System.out.println("handleWallButtonClick");
            System.out.println("currentEntranceArrayIndex:"+currentEntranceArrayIndex+", currentExitArrayIndex:"+currentExitArrayIndex);
            System.out.println("startX:"+startX+", startY:"+startY);
            System.out.println("endX:"+endX+", endY:"+endY);
            System.out.println("entranceCount:"+entranceCount);*/
        }
    }
	
	//準備ボタンが押されたとき
    private void handleReadyButtonClick() {
        if (currentEntrance == null || currentExit == null) {//入口と出口を設定させる
            JOptionPane.showMessageDialog(null, "入口と出口を設定してください", "エラー", JOptionPane.ERROR_MESSAGE);
            return;
        }
        //System.out.println("準備ボタンをおした：" + myName);

        // 迷宮情報を文字列に変換
        StringBuilder mazeInfo = new StringBuilder();//文字情報を追加していけるため，StringBuilder使用（文字として迷宮管理）
        mazeInfo.append("MAZE_INFO ").append(myName).append(" ");//迷宮情報＋プレイヤーの名前＋空白
        
        // 入口と出口の位置を追加
        if (currentEntrance != null) {
            String entrancePos = currentEntrance.getActionCommand();
            mazeInfo.append("E").append(entrancePos).append(" ");//E（entrance）＋入口の位置＋空白
        }
        if (currentExit != null) {
            String exitPos = currentExit.getActionCommand();
            mazeInfo.append("X").append(exitPos).append(" ");//X（exit）＋出口の位置＋空白
        }
        
        // 壁の情報を追加（冗長になっていたのを２行にまとめた）
		/*for (int i = 0; i < walls.length; i++){
			int[] wall = walls[i];
			// 壁のX座標とY座標を個別に取得
			int wallX = wall[0];
			int wallY = wall[1];
			mazeInfo.append("W").append(wallX).append(",").append(wallY).append(" ");
		}*/
        for (int[] wall : walls) {
            mazeInfo.append("W").append(wall[0]).append(",").append(wall[1]).append(" ");
        }
        
        // 迷宮情報を送信
        out.println(mazeInfo.toString());
        out.flush();

        // 準備完了を送信
        out.println("READY " + myName);
        out.flush();
        isReady = true;
        
        // 自分が先行かどうかを設定（相手がまだ準備完了していない場合は先行）
        isFirstPlayer = !opponentReady;
        isMyTurn = isFirstPlayer;
        
        // 準備完了ボタンを無効化
        readyButton.setEnabled(false);
        
        // UI更新（入力の無効化）
        disableInput();
        
        // ゲーム開始の準備
        if (opponentReady) {
            // 相手が既に準備完了なら後攻として開始
            isFirstPlayer = false;
            isMyTurn = false;
            out.println("TURN_ORDER " + myName + " " + isFirstPlayer);
            out.flush();
            startGame();
        } else {
            // 相手がまだ準備完了していない場合は先行として待機
            isFirstPlayer = true;
            isMyTurn = true;
            SwingUtilities.invokeLater(() -> {// タイトル：準備完了，メッセージ：相手の準備が完了するまでお待ちください
                JOptionPane.showMessageDialog(null, "相手の準備が完了するまでお待ちください", "準備完了", JOptionPane.INFORMATION_MESSAGE);
            });
        }

    }

    private synchronized void startGame() {//相手の画面との同期はできないため，thisかsynchronizedで迷ったがこっちにしてみた．エラーだらけ，消すかも
        //System.out.println("startGameを呼び出した： " + myName);
        
        if (!isReady || !opponentReady) {//準備できてるかどうか
            //System.out.println("全員準備できていないので開始できません");
            return;
        }

        if (gameScreen != null) {// すでにゲームが開始されている場合
            //System.out.println("ゲームはすでに始まっています");
            return;
        }

        SwingUtilities.invokeLater(() -> {// この書き方だと，Runnable作らなくていいし，@Overrideいらないらしい
            try {
                //System.out.println("ゲーム画面更新開始");
                
                String message = "相手の準備が完了しました。\n" +
								 "あなたは入口から開始して、出口を目指します。";
                JOptionPane.showMessageDialog(null, message, "ゲーム開始", JOptionPane.INFORMATION_MESSAGE);
                
                // ゲーム画面の生成
                Container contentPane = getContentPane();//ペイン取得
                contentPane.removeAll();//一旦ペイン全部はがす
                gameScreen = new GameScreen(room, out, myName);//gameScreen生成
                gameScreen.setBounds(0, 0, 2000, 1200);
                contentPane.add(gameScreen);//ペインに張り付け
                
                // ゲーム開始メッセージの表示
                gameScreen.addChatMessage("システム: ゲームを開始します");
                if (isFirstPlayer) {
                    gameScreen.addChatMessage("システム: [ " + myName + " ]が先行です");
                    gameScreen.addChatMessage("システム: [ " + myName + " ]からゲームを開始してください");
                    gameScreen.isMyTurn = true;
                    gameScreen.enableMovement();
                } else {
                    gameScreen.addChatMessage("システム: [ " + myName + " ]が後攻です");
                    gameScreen.addChatMessage("システム: 相手プレイヤーからゲームを開始します");
                    gameScreen.isMyTurn = false;
                    gameScreen.disableMovement();
                }
                
                validate();// レイアウト更新
                repaint();// 再描画
                //System.out.println("ゲーム画面の設定完了 " + (isFirstPlayer ? "Player1" : "Player2")); //初めに準備完了おしたプレイヤーを確かめ
            } catch (Exception e) {
                System.out.println("ゲーム画面の設定中にエラー: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void disableInput() {//準備完了ボタンが押されたときに入力を無効化する
        // 部屋ボタンを無効化
        for(int i = 0; i < room.length; i++) {
            for(int j = 0; j < room[i].length; j++) {
                room[i][j].setEnabled(false);
            }
        }
        // 縦の壁ボタンを無効化
        for(int i = 0; i < wallvertical.length; i++) {
            for(int j = 0; j < wallvertical[i].length; j++) {
                wallvertical[i][j].setEnabled(false);
            }
        }
        // 横の壁ボタンを無効化
        for(int i = 0; i < wallhorizontal.length; i++) {
            for(int j = 0; j < wallhorizontal[i].length; j++) {
                wallhorizontal[i][j].setEnabled(false);
            }
        }
    }

    // 押されたボタンの判定用，部屋，壁の順（後付けしたものなので統合するかも） 
    private boolean isRoomButton(JButton button) {//roomボタンが押されたときの判定用
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (button == room[i][j]) {
                    return true;
                }
            }
        }
        return false;
    }
    private boolean isWallButton(JButton button) {//wallボタンが押されたときの判定用
        // 縦壁のチェック
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 5; j++) {
                if (button == wallvertical[i][j]) {
                    return true;
                }
            }
        }
        // 横壁のチェック
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 6; j++) {
                if (button == wallhorizontal[i][j]) {
                    return true;
                }
            }
        }
        return false;//一応falseを返す
    }
	
    private void updateGridPosition(JButton button, int value) {//新しいgridの座標とその属性（入口や出口）を設定
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (button == room[i][j]) {
                    grid[i*2][j*2] = value;//部屋ボタンがクリックされたとき，gridのvalueを設定
                    break;
                }
            }
        }
    }

    private void resetGridPosition(JButton button) {//gridを0（何もない通路，background）に変更
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (button == room[i][j]) {
                    grid[i*2][j*2] = 0;//grid 0（通路）に変更
                    break;
                }
            }
        }
    }

    private void setStartPosition(JButton button) {//開始位置を設定，後で幅優先探索につかうため
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (button == room[i][j]) {
                    startPosition = new int[]{i, j};
                    break;
                }
            }
        }
    }

    private void setEndPosition(JButton button) {//ゴール位置を設定，後で幅優先探索につかうため
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (button == room[i][j]) {
                    endPosition = new int[]{i, j};
                    break;
                }
            }
        }
    }
	//mouseListenerの設定のため必要
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mouseDragged(MouseEvent e) {}
    public void mouseMoved(MouseEvent e) {}

	//壁についての設定（判定込み）
	private void toggleWall(JButton button, int buttonIndex, int col, boolean isVertical) {
		if (button.getIcon() == walloff) {//もし，クリックした場所に壁が設定されていなければ
			if (wallCount >= 20) {//壁が20枚以上あったらそれ以上おけない
				JOptionPane.showMessageDialog(null, "壁は最大20枚までしか設置できません", "エラー", JOptionPane.ERROR_MESSAGE);
				return;
			}

			if (!canPlaceWall(buttonIndex, col, isVertical)) {//壁が置けるかどうかの判定
				JOptionPane.showMessageDialog(null, "そこには壁を置けません", "エラー", JOptionPane.ERROR_MESSAGE);
				return;
			}

			int baseIndex;//壁の判別のために，それぞれの数値に足した数
			if (isVertical) {
				baseIndex = 100;//縦壁
			} else {
				baseIndex = 200;
			}

			int index;//壁のインデックス（位置）を計算
			if (isVertical) {
				index = (buttonIndex * 5 + col) + baseIndex;
			} else {
				index = (buttonIndex * 6 + col) + baseIndex;
			}

			int gridRow;//壁の行の位置（偶数と奇数で区別）
			if (isVertical) {
				gridRow = buttonIndex * 2;
			} else {
				gridRow = buttonIndex * 2 + 1;
			}

			int gridCol;//壁の列の位置（偶数と奇数で区別）
			if (isVertical) {
				gridCol = col * 2 + 1;
			} else {
				gridCol = col * 2;
			}

			button.setIcon(wallon);
			grid[gridRow][gridCol] = 1;
			addWall(index, col);
			wallCount++;
			//デバッグ用
			if (isVertical){//垂直のとき
				System.out.println("垂直の壁");
				System.out.println("index:"+index);
				System.out.println("gridRow:"+gridRow);
				System.out.println("gridCol:"+gridCol);
			} else {//水平の時
				System.out.println("水平の壁");
				System.out.println("index:"+index);
				System.out.println("gridRow:"+gridRow);
				System.out.println("gridCol:"+gridCol);
			}
		} else {//壁を消すとき
			int baseIndex;//上でもあるもの
			if (isVertical) {
				baseIndex = 100;
			} else {
				baseIndex = 200;
			}

			int index;//
			if (isVertical) {
				index = (buttonIndex * 5 + col) + baseIndex;
			} else {
				index = (buttonIndex * 6 + col) + baseIndex;
			}

			// 壁を消す位置のgrid座標を計算
			int gridRow = buttonIndex * 2; // 基準となる行
			int gridCol = col * 2; // 基準となる列

			if (isVertical) {
			    gridCol++; // 垂直の壁は奇数列
			} else {
			    gridRow++; // 水平の壁は奇数行
			}

			button.setIcon(walloff);
			grid[gridRow][gridCol] = 0;
			removeWall(index, col);
			wallCount--;
			/*
			//デバッグ用
			if (isVertical){//垂直の壁削除
				System.out.println("垂直の壁削除");
				System.out.println("index:"+index);
				System.out.println("gridRow:"+gridRow);
				System.out.println("gridCol:"+gridCol);
			} else {//水平の時
				System.out.println("水平の壁削除");
				System.out.println("index:"+index);
				System.out.println("gridRow:"+gridRow);
				System.out.println("gridCol:"+gridCol);
			}
			*/
		}
		
	}

	//壁を置けるかどうかの判定
	private boolean canPlaceWall(int row, int col, boolean isVertical) {
		// 入口と出口が設定されていない場合は壁を置けない
		if (currentEntrance == null || currentExit == null) {
			return false;
		}

		// 現在のgridの状態を一時保存
		int[][] tempGrid = copyGrid(grid);

		// 仮に壁を設置
		// 壁のgrid座標を計算
		int gridRow = row * 2; // 基準となる行を設定
		int gridCol = col * 2; // 基準となる列を設定

		if (isVertical) {
		    gridCol++; // 垂直の壁は奇数列に配置
		} else {
		    gridRow++; // 水平の壁は奇数行に配置
		}

		tempGrid[gridRow][gridCol] = 1; // 壁として設定

		// 入口と出口の座標を取得
		int startX = getGridX(currentEntrance);
		int startY = getGridY(currentEntrance);
		int endX = getGridX(currentExit);
		int endY = getGridY(currentExit);

		// 経路が存在するか確認
		boolean hasPath = isPathExistOnGrid(tempGrid, startX, startY, endX, endY);

		return hasPath;
	}

	//canplaceで通路が通っているかの確認（ゴールできる通路が確保されているか）
    private boolean isPathExist(int startX, int startY, int endX, int endY) {
        //printGrid();//壁の位置を表示（デバッグ用）
        
        boolean[][] visited = new boolean[11][11];//visitedの初期化
        Queue<int[]> queue = new LinkedList<>();//幅優先探索のリスト

        // 初期位置をキューに追加
        queue.offer(new int[]{startX, startY});
        visited[startX][startY] = true;

        // 上下左右の移動方向（11x11グリッドでの移動）
        int[] dx = {0, 0, -1, 1}; // 左右の移動は1マス単位
        int[] dy = {-1, 1, 0, 0}; // 上下の移動は1マス単位

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0];
            int y = current[1];

            // 4方向の探索
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];

                // 移動先が迷路の範囲内で、未訪問かつ通過可能な場合
                if (isInside(nx, ny) && !visited[nx][ny]) {
					if (grid[nx][ny] == 0) {
                        if (nx != startX || ny != startY) {
                            queue.offer(new int[]{nx, ny});
                            visited[nx][ny] = true;
                        }
					} else if (grid[nx][ny] == 3) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
	/*
    // gridの状態を表示（デバッグ用）
    private void printGrid() {
        System.out.println("grid:\n");
        for (int i = 0; i < 11; i++) {
            for (int j = 0; j < 11; j++) {
                System.out.print(grid[i][j] + " ");
            }
            System.out.println();
        }
    }
	*/
	
	// 判定が枠内に収まっているかの判定のみ
    private boolean isInside(int x, int y) {
        return x >= 0 && x < grid.length && y >= 0 && y < grid[0].length;
    }

	//壁を追加するだけの処理
    private void addWall(int x, int y) {
        if (!wallExists(x, y)) {
            walls.add(new int[]{x, y});
        }
    }

	//壁を消すだけの処理
    private void removeWall(int x, int y) {
        walls.removeIf(wall -> wall[0] == x && wall[1] == y);
    }

	//壁が存在するかどうかの比較のみ
    private boolean wallExists(int x, int y) {
        for (int[] wall : walls) {
            if (wall[0] == x && wall[1] == y) {
                return true;
            }
        }
        return false;
    }

	//準備完了ボタンを押せるかどうかの確認のみ
    private void updateReadyButtonState() {
        readyButton.setEnabled(currentEntrance != null && currentExit != null);
    }

    // ゲーム画面の実装（クラスの中にクラスを実装できるのか・・・？）
// 相手の迷路情報を一時保存するための静的フィールド
private static ArrayList<int[]> tempOpponentWalls = new ArrayList<>();
private static int[][] tempOpponentGrid = new int[11][11];
private static boolean hasTempMazeInfo = false;

// GameScreenクラス: ゲーム画面の主要コンポーネントを管理するクラス
private class GameScreen extends JPanel {
    // プレイヤーとゲーム状態を管理する変数
    private JButton[][] leftRoom;        // 左側の迷宮（相手が攻略する側）のボタン配列
    private JButton[][] rightRoom;       // 右側の迷宮（自分が攻略する側）のボタン配列
    private JTextArea chatArea;          // チャットメッセージを表示するエリア
    private JTextField moveInput;         // 移動コマンドの入力フィールド
    private JTextField chatInput;         // チャットメッセージの入力フィールド
    private JButton sendButton;          // 移動コマンドの送信ボタン
    private JButton chatButton;          // チャットメッセージの送信ボタン
    private PrintWriter out;             // サーバーへのメッセージ送信用
    private int playerX = -1;            // プレイヤーのX座標（初期値-1は未配置状態）
    private int playerY = -1;            // プレイヤーのY座標（初期値-1は未配置状態）
    private int opponentX = -1;          // 相手プレイヤーのX座標
    private int opponentY = -1;          // 相手プレイヤーのY座標
    private ImageIcon playerIcon;        // 自分のプレイヤーアイコン（赤色）
    private ImageIcon opponentIcon;      // 相手のプレイヤーアイコン（青色）
    private String myName;               // プレイヤーの名前
    private boolean gameEnded = false;   // ゲーム終了フラグ
    private boolean isMyTurn = true;     // ターン管理フラグ
    private javax.swing.Timer moveTimer; // 移動時の遅延処理用タイマー
    private ArrayList<Point> moveHistory = new ArrayList<>(); // 移動履歴を保存
    private int[][] gameGrid;            // ゲーム用の迷路グリッド
    private ArrayList<int[]> opponentWalls = new ArrayList<>(); // 相手の壁情報
    private int[][] opponentGrid = new int[11][11];             // 相手の迷路グリッド

    // 壁の判定を行うメソッド
    // 現在位置から新しい位置への移動が可能かどうかをチェック
    private boolean checkWall(int newRow, int newCol) {
        // グリッド座標に変換（6x6から11x11への変換）
        // 通路と壁を含む詳細なグリッドでの座標を計算
        int currentRow = playerX * 2;    // 現在の行を11x11グリッドの座標に変換
        int currentCol = playerY * 2;    // 現在の列を11x11グリッドの座標に変換
        int newGridRow = newRow * 2;     // 移動先の行を11x11グリッドの座標に変換
        int newGridCol = newCol * 2;     // 移動先の列を11x11グリッドの座標に変換

        // 移動方向を特定（上下左右の移動量を計算）
        int dx = Integer.compare(newGridRow, currentRow);  // 行方向の移動量（-1, 0, 1）
        int dy = Integer.compare(newGridCol, currentCol);  // 列方向の移動量（-1, 0, 1）

        // 現在位置から移動先までの経路をチェック
        boolean foundWall = false;       // 壁があるかどうかのフラグ
        Point wallLocation = null;       // 見つかった壁の位置
        int x = currentRow;              // チェック位置（行）
        int y = currentCol;              // チェック位置（列）

        // 現在位置から移動先まで1マスずつ確認
        while (x != newGridRow || y != newGridCol) {
            // 次のマスの座標を計算
            Point wallCheck = new Point(
                dx != 0 ? x + dx : x,    // 縦方向の移動がある場合は行を更新
                dy != 0 ? y + dy : y     // 横方向の移動がある場合は列を更新
            );

            // 移動先のマスに壁があるかチェック
            if (opponentGrid[wallCheck.x][wallCheck.y] == 1) {
                foundWall = true;        // 壁を発見
                wallLocation = wallCheck; // 壁の位置を記録
                break;                   // 壁が見つかったら探索終了
            }

            // 次のマスへ移動
            x += dx;
            y += dy;
        }

        // 壁が見つかった場合、その壁を表示
        if (foundWall && wallLocation != null) {
            JButton wallBtn = wallButtons.get(wallLocation);
            if (wallBtn != null) {
                wallBtn.setIcon(wallon);         // 壁のアイコンを設定
                wallBtn.setOpaque(true);         // ボタンを不透明に
                wallBtn.setContentAreaFilled(true);
                wallBtn.setBorderPainted(true);
                wallBtn.setVisible(true);        // 壁を表示
                visibleWalls.add(wallLocation);  // 表示済みの壁リストに追加
            }
        } else {
            // 壁がない場合、移動先が出口かどうかをチェック
            if (opponentGrid[newGridRow][newGridCol] == 3) {
                return false;    // 出口の場合は移動可能
            }
        }
        return foundWall;    // 壁があれば true、なければ false を返す
    }

    public GameScreen(JButton[][] myMaze, PrintWriter out, String myName) {
        this.myName = myName;
        playerIcon = new ImageIcon(new File("myicon.png").getAbsolutePath());
        opponentIcon = new ImageIcon(new File("youricon.png").getAbsolutePath());
        this.out = out;
        setLayout(null);
        setOpaque(false);

        // 背景用のJLabelを作成
        JLabel backgroundLabel = new JLabel(secondBackgroundImage) {
            @Override
            public void paintComponent(Graphics g) {
                g.drawImage(secondBackgroundImage.getImage(), 0, 0, getWidth(), getHeight(), this);
            }
        };
        backgroundLabel.setBounds(0, 0, 2000, 1200);
        
        // メインパネル（他のコンポーネントを含む）を作成
        JPanel mainPanel = new JPanel(null);
        mainPanel.setBounds(0, 0, 2000, 1200);
        mainPanel.setOpaque(false);

        // 背景とメインパネルを追加
        add(backgroundLabel);
        add(mainPanel);
        
        // メインパネルに対してコンポーネントを追加
        setupPanels(myMaze, mainPanel);
        setupChatArea(mainPanel);
        setupMoveInput(mainPanel);

        // レイヤーの順序を設定
        setComponentZOrder(mainPanel, 0);    // 前面
        setComponentZOrder(backgroundLabel, 1); // 背面
    }

    private void setupPanels(JButton[][] myMaze, JPanel parent) {
        // ボタンサイズを小さくする
        int buttonSize = 80;
        int spacing = 90;

        // 左パネル（相手が攻略する迷宮）
        JPanel leftPanel = new JPanel(null);
        leftPanel.setBounds(100, 250, 700, 700);  // Y座標を250に変更
        leftPanel.setBackground(Color.LIGHT_GRAY);
        
        // 右パネル（自分が攻略する迷宮）- 位置を右に移動
        JPanel rightPanel = new JPanel(null);
        rightPanel.setBounds(1200, 250, 700, 700);
        rightPanel.setBackground(Color.LIGHT_GRAY);

        // 座標ラベルの追加（左パネル）
        for(int i=0; i<6; i++) {
            // 数字（1-6）
            JLabel numLabel = new JLabel(String.valueOf(i + 1));
            numLabel.setFont(new Font("Arial", Font.BOLD, 20));
            numLabel.setBounds(i * spacing + 45, 0, 30, 20);
            leftPanel.add(numLabel);

            // アルファベット（A-F）
            JLabel alphaLabel = new JLabel(String.valueOf((char)('A' + i)));
            alphaLabel.setFont(new Font("Arial", Font.BOLD, 20));
            alphaLabel.setBounds(0, i * spacing + 45, 20, 20);
            leftPanel.add(alphaLabel);
        }

        // 座標ラベルの追加（右パネル）
        for(int i=0; i<6; i++) {
            // 数字（1-6）
            JLabel numLabel = new JLabel(String.valueOf(i + 1));
            numLabel.setFont(new Font("Arial", Font.BOLD, 20));
            numLabel.setBounds(i * spacing + 45, 0, 30, 20);
            rightPanel.add(numLabel);

            // アルファベット（A-F）
            JLabel alphaLabel = new JLabel(String.valueOf((char)('A' + i)));
            alphaLabel.setFont(new Font("Arial", Font.BOLD, 20));
            alphaLabel.setBounds(0, i * spacing + 45, 20, 20);
            rightPanel.add(alphaLabel);
        }

        leftRoom = new JButton[6][6];
        rightRoom = new JButton[6][6];

        // 左パネル（相手が攻略する迷宮）には自分が作成した迷宮を表示（壁あり）
        initializeMaze(leftRoom, myMaze, leftPanel, true);
        
        // 右パネル（自分が攻略する迷宮）には入口と出口のみを表示（壁なし）
        initializeMaze(rightRoom, myMaze, rightPanel, false);

        parent.add(leftPanel);
        parent.add(rightPanel);
    }

    private Map<Point, JButton> wallButtons = new HashMap<>();
    private Set<Point> visibleWalls = new HashSet<>();
    
    private void initializeMaze(JButton[][] targetMaze, JButton[][] sourceMaze, JPanel panel, boolean showWalls) {
        // ボタンサイズを小さくする
        int buttonSize = 80;  // 100から80に縮小
        int spacing = 90;     // 110から90に縮小

        // まずボタンを初期化
        for(int i = 0; i < 6; i++) {
            for(int j = 0; j < 6; j++) {
                targetMaze[i][j] = new JButton();
                targetMaze[i][j].setBounds(j*spacing+25, i*spacing+25, buttonSize, buttonSize);
                targetMaze[i][j].setEnabled(false);
                targetMaze[i][j].setBackground(new Color(240, 240, 240)); // 明るい灰色の背景
                targetMaze[i][j].setOpaque(true);
                targetMaze[i][j].setContentAreaFilled(true);
                targetMaze[i][j].setBorderPainted(true);
                // アイコンを前面に表示
                targetMaze[i][j].setHorizontalAlignment(SwingConstants.CENTER);
                targetMaze[i][j].setVerticalAlignment(SwingConstants.CENTER);
                targetMaze[i][j].setIconTextGap(0);
                panel.add(targetMaze[i][j]);
            }
        }

        // 右側の迷路の場合、壁ボタンを初期化
        if (!showWalls) {
            // 縦の壁
            for(int i = 0; i < 6; i++) {
                for(int j = 0; j < 5; j++) {
                    JButton wallButton = new JButton(walloff);
                    wallButton.setBounds(j*spacing+105, i*spacing+25, 10, buttonSize);
                    wallButton.setEnabled(false);
                    wallButton.setVisible(false);
                    panel.add(wallButton);
                    wallButtons.put(new Point(i*2, j*2+1), wallButton);
                }
            }

            // 横の壁
            for(int i = 0; i < 5; i++) {
                for(int j = 0; j < 6; j++) {
                    JButton wallButton = new JButton(walloff);
                    wallButton.setBounds(j*spacing+25, i*spacing+105, buttonSize, 10);
                    wallButton.setEnabled(false);
                    wallButton.setVisible(false);
                    panel.add(wallButton);
                    wallButtons.put(new Point(i*2+1, j*2), wallButton);
                }
            }
        }

        if (showWalls) {
            // 左側の迷宮：自分の設定を表示
            for(int i = 0; i < 6; i++) {
                for(int j = 0; j < 6; j++) {
                    Icon icon = sourceMaze[i][j].getIcon();
                    if (icon == entrance) {
                        targetMaze[i][j].setIcon(entrance);
                        targetMaze[i][j].setDisabledIcon(entrance); // 無効化状態でも同じアイコンを表示
                    } else if (icon == exit) {
                        targetMaze[i][j].setIcon(exit);
                        targetMaze[i][j].setDisabledIcon(exit); // 無効化状態でも同じアイコンを表示
                    }
                }
            }
            
            // 自分の壁を表示
            for (int[] wall : walls) {// 壁すべてに処理する（for a:b)
                int wallX = wall[0];
                int wallY = wall[1];
                JButton wallButton = new JButton();
                wallButton.setIcon(wallon);
                wallButton.setEnabled(false);

                if (wallX >= 100 && wallX < 200) { // 垂直の壁
                    int row = (wallX - 100) / 5;
                    int col = (wallX - 100) % 5;
                    wallButton.setBounds(col*spacing+105, row*spacing+25, 10, buttonSize);
                } else if (wallX >= 200) { // 水平の壁
                    int row = (wallX - 200) / 6;
                    int col = (wallX - 200) % 6;
                    wallButton.setBounds(col*spacing+25, row*spacing+105, buttonSize, 10);
                }
                panel.add(wallButton);
            }
        } else {
            // 右側の迷宮：相手の設定を表示
            for(int i = 0; i < 11; i += 2) {
                for(int j = 0; j < 11; j += 2) {
                    int value = tempOpponentGrid[i][j];  // tempOpponentGridを使用
                    int roomI = i/2;
                    int roomJ = j/2;
                    
                    if (value == 2) { // 入口
                        targetMaze[roomI][roomJ].setIcon(entrance);
                        targetMaze[roomI][roomJ].setDisabledIcon(entrance);
                        // プレイヤーの初期位置を設定
                        if (playerX == -1 && playerY == -1) {
                            updatePlayerPosition(roomI, roomJ);
                        }
                    } else if (value == 3) { // 出口
                        targetMaze[roomI][roomJ].setIcon(exit);
                        targetMaze[roomI][roomJ].setDisabledIcon(exit);
                    }
                }
            }

            // プレイヤーの現在位置を表示
            if (playerX != -1 && playerY != -1) {
                targetMaze[playerX][playerY].setIcon(playerIcon);
                targetMaze[playerX][playerY].setDisabledIcon(playerIcon);
            }

            // 相手の壁情報を設定
            opponentGrid = tempOpponentGrid.clone();
            opponentWalls.clear();
            opponentWalls.addAll(tempOpponentWalls);
        }

        // 実際の壁の位置を記録
        for (int[] wall : opponentWalls) {
            int wallX = wall[0];
            if (wallX >= 100 && wallX < 200) {
                int row = (wallX - 100) / 5;
                int col = (wallX - 100) % 5;
                opponentGrid[row*2][col*2+1] = 1;
            } else if (wallX >= 200) {
                int row = (wallX - 200) / 6;
                int col = (wallX - 200) % 6;
                opponentGrid[row*2+1][col*2] = 1;
            }
        }
    }

    // チャットエリアのセットアップ
    // チャットの表示領域とメッセージ入力用のUIコンポーネントを設定
    private void setupChatArea(JPanel parent) {
        // チャットエリアの位置を調整
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setFont(new Font("Meiryo", Font.PLAIN, 16));
        
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBounds(850, 250, 300, 400);  // Y座標を250に変更
        parent.add(scrollPane);

        chatInput = new JTextField();
        chatButton = new JButton("送信");
        
        // チャットボタンのアクションリスナーを設定
        chatButton.addActionListener(e -> {
            String message = chatInput.getText().trim();
            if (!message.isEmpty()) {
                // サーバーにメッセージを送信
                out.println("CHAT " + myName + " " + message);
                out.flush();
                // 自分のメッセージをチャットエリアに表示
                addChatMessage("あなた: " + message);
                // 入力フィールドをクリア
                chatInput.setText("");
            }
        });

        // Enterキーでもメッセージを送信できるようにする
        chatInput.addActionListener(e -> chatButton.doClick());
        
        // チャット入力部分の位置を調整
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBounds(850, 660, 300, 30);  // Y座標を660に変更
        chatPanel.add(chatInput, BorderLayout.CENTER);
        chatPanel.add(chatButton, BorderLayout.EAST);
        parent.add(chatPanel);
    }

    // 移動入力機能のセットアップ
    // プレイヤーの移動命令を入力・処理するUIコンポーネントを設定
    private void setupMoveInput(JPanel parent) {
        // 移動入力部分の位置を調整
        JLabel moveLabel = new JLabel("移動宣言マス：（例：B2）");
        moveLabel.setFont(new Font("Meiryo", Font.BOLD, 16));
        moveLabel.setBounds(850, 700, 300, 30);  // Y座標を700に変更
        parent.add(moveLabel);

        moveInput = new JTextField();  // 移動コマンド入力用のテキストフィールド
        moveInput.setFont(new Font("Meiryo", Font.PLAIN, 14));
        sendButton = new JButton("移動");  // 移動実行ボタン
        sendButton.setFont(new Font("Meiryo", Font.PLAIN, 14));
        moveTimer = new javax.swing.Timer(2000, null);  // 移動判定用の2秒タイマー
        moveTimer.setRepeats(false);  // タイマーは1回だけ実行

        // 移動ボタンのアクションリスナーを設定
        // 移動処理の中核となる部分
        sendButton.addActionListener(e -> { 
            System.out.println("=== 移動処理開始 ===");
            System.out.println("現在の状態:");
            System.out.println("プレイヤー位置: (" + playerX + "," + playerY + ")");
            System.out.println("ターン: " + (isMyTurn ? "自分" : "相手"));
            System.out.println("ゲーム状態: " + (gameEnded ? "終了" : "進行中"));

            if(gameEnded) {
                System.out.println("ゲーム終了済み");
                JOptionPane.showMessageDialog(this, "ゲームは終了しています。");
                return;
            }

            if(!isMyTurn) {
                System.out.println("相手のターン");
                JOptionPane.showMessageDialog(this, "相手のターンです。しばらくお待ちください。");
                disableMovement();
                return;
            }

            String move = moveInput.getText().trim().toUpperCase();
            System.out.println("入力された移動: " + move);

            if(!isValidMove(move)) {
                System.out.println("不正な移動形式");
                JOptionPane.showMessageDialog(this, "正しい形式で入力してください（例：B2）");
                return;
            }

            int newRow = move.charAt(0) - 'A';
            int newCol = move.charAt(1) - '1';
            //System.out.println("移動先座標: (" + newRow + "," + newCol + ")");
            //System.out.println("グリッド移動先: (" + (newRow * 2) + "," + (newCol * 2) + ")");

            if(!isValidMovement(newRow, newCol)) {
                System.out.println("無効な移動: 非隣接または同じ位置");
                JOptionPane.showMessageDialog(this, "そのマスには移動できません。\n隣接するマスにのみ移動可能です。");
                return;
            }

            //System.out.println("移動前の状態:");
            //System.out.println("opponentGrid[" + (newRow * 2) + "][" + (newCol * 2) + "] = " + opponentGrid[newRow * 2][newCol * 2]);

            // 移動処理開始前にターンを終了し、入力を無効化
            isMyTurn = false;
            disableMovement();
            System.out.println("Turn disabled, waiting for wall check");
            
            // 自分側の移動宣言表示
            addChatMessage("システム　>> [ " + myName + " ] が " + move + " へ移動：判定中...");
            // 相手側への移動宣言通知
            out.println("CHAT MOVEMENT システム：[ " + myName + " ] が " + move + " へ移動しようとしています");
            out.flush();

            // 移動判定用のタイマーをリセット
            if (moveTimer != null) {
                moveTimer.stop();
            }
            
            // 変数をfinalとして宣言
            final String finalMove = move;
            final int finalNewRow = newRow;
            final int finalNewCol = newCol;
            
            //判定に2秒かける
            moveTimer = new javax.swing.Timer(2000, event -> {
                //System.out.println("=== 移動判定開始 ===");
                boolean hitWall = checkWall(finalNewRow, finalNewCol);
                
                if (!hitWall) {
                    // 移動成功
                    addChatMessage("システム　>> [ " + myName + " ] が " + finalMove + " へ移動：yes, 進めます");
                    out.println("MOVE " + myName + " " + finalMove);
                    out.flush();
                    updatePlayerPosition(finalNewRow, finalNewCol);
                    moveInput.setText("");

                    // ゴール判定
                    if(opponentGrid[finalNewRow * 2][finalNewCol * 2] == 3) {
                        System.out.println("ゴール到達");
                        gameEnded = true;
                        JOptionPane.showMessageDialog(this, "ゴールに到達しました！おめでとうございます！");
                        addChatMessage("システム: [ " + myName + " ] がゴールに到達し、勝利しました！");
                        out.println("GOAL " + myName);
                        out.flush();
                        disableMovement();
                    } else {
                        // 移動継続 - 壁に当たっていないので同じプレイヤーのターン
                        System.out.println("移動継続 - 同じプレイヤーのターン");
                        isMyTurn = true;
                        enableMovement();
                        addChatMessage("壁に当たっていないため、続けてあなたの番です");
                    }
                } else {
                    // 壁に衝突
                    System.out.println("壁に衝突: " + finalMove);
                    addChatMessage("システム　>> [ " + myName + " ] が " + finalMove + " へ移動：no, 壁です");
                    
                    // サーバーに通知
                    out.println("WALL_HIT " + myName);
                    out.flush();
                    addChatMessage("壁に当たりました。次は相手の番です");
                    
                    // ターン終了
                    disableMovement();
                }
                System.out.println("=== 移動判定終了 ===");
            });
            moveTimer.setRepeats(false); // タイマーは1回だけ実行
            moveTimer.start();
        });

        JPanel movePanel = new JPanel(new BorderLayout());
        movePanel.setBounds(850, 740, 300, 30);  // Y座標を740に変更
        movePanel.add(moveInput, BorderLayout.CENTER);
        movePanel.add(sendButton, BorderLayout.EAST);
        parent.add(movePanel);
    }

    // 入力された移動コマンドの形式が正しいかを検証
    // 正しい形式: アルファベット[A-F]と数字[1-6]の組み合わせ
    private boolean isValidMove(String move) {
        return move.matches("^[A-F][1-6]$");  // 正規表現でフォーマットをチェック
    }

    // 移動先が有効な位置かどうかを判定
    // 隣接するマスへの移動のみを許可し、同じ場所や斜めへの移動を禁止
    private boolean isValidMovement(int newRow, int newCol) {
        // 初回移動時（プレイヤーが未配置）は入口（0,0）のみ許可
        if(playerX == -1 || playerY == -1) {
            return newRow == 0 && newCol == 0;
        }
        
        // 現在位置と同じ場所への移動を禁止
        if (newRow == playerX && newCol == playerY) {
            return false;
        }

        // 直前の移動位置への移動を禁止（行ったり来たりを防止）
        if (!moveHistory.isEmpty()) {
            Point lastMove = moveHistory.get(moveHistory.size() - 1);
            if (lastMove.x == newCol * 110 + 60 && lastMove.y == newRow * 110 + 60) {
                return false;
            }
        }

        // 隣接マスへの移動のみ許可
        return Math.abs(newRow - playerX) + Math.abs(newCol - playerY) == 1;
    }

    // 移動入力を有効化
    // プレイヤーのターン開始時に呼び出される
    private void enableMovement() {
        moveInput.setEnabled(true);  // 入力フィールドを有効化
        sendButton.setEnabled(true);  // 移動ボタンを有効化
        moveInput.requestFocus();    // 入力フィールドにフォーカスを設定
    }

    // 移動入力を無効化
    // 相手のターンや判定中に呼び出される
    private void disableMovement() {
        moveInput.setEnabled(false);   // 入力フィールドを無効化
        sendButton.setEnabled(false);  // 移動ボタンを無効化
        moveInput.setText("");         // 入力フィールドをクリア
    }

    // チャットエリアにメッセージを追加
    public void addChatMessage(String message) {
        chatArea.append(message + "\n");  // メッセージを追加して改行
        // 最新のメッセージが見えるようにスクロール
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // コンポーネントの描画処理
	/*
    // 移動履歴の軌跡を描画する(実装しきれなかった)
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(Color.RED);  // 軌跡の色を赤に設定
        g2d.setStroke(new BasicStroke(3));  // 線の太さを3ピクセルに設定

        // 移動履歴の線を描画
        if (moveHistory.size() > 1) {
            for (int i = 0; i < moveHistory.size() - 1; i++) {
                Point p1 = moveHistory.get(i);     // 移動元の座標
                Point p2 = moveHistory.get(i + 1); // 移動先の座標
                g2d.drawLine(p1.x, p1.y, p2.x, p2.y);  // 2点間に線を引く
            }
        }
    }
	*/
	
    public void updateOpponentPosition(int row, int col) {
        // 座標の範囲チェック
        if (row < 0 || row >= 6 || col < 0 || col >= 6) {
            System.err.println("警告: 不正な座標が指定されました - row: " + row + ", col: " + col);
            return;
        }

        if (opponentX != -1 && opponentY != -1 && opponentX < 6 && opponentY < 6) {
            leftRoom[opponentX][opponentY].setIcon(null);
        }
        opponentX = row;
        opponentY = col;
        leftRoom[row][col].setIcon(opponentIcon);
        leftRoom[row][col].setDisabledIcon(opponentIcon); // 無効化状態でも同じアイコンを表示

        if(row == 5 && col == 5) {
            gameEnded = true;
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "相手がゴールに到達しました。", "敗北...", JOptionPane.INFORMATION_MESSAGE);
                addChatMessage("システム: 相手プレイヤーがゴールに到達しました。あなたの負けです。");
                disableMovement();
            });
        } else if (!gameEnded) {
            // このメッセージは受動的なものなので、実際のターン変更はWALL_HITメッセージで処理
            addChatMessage("相手の移動が成功しました");
            isMyTurn = false;  // 相手の移動が成功したので、まだ相手のターン
            disableMovement(); // 入力を無効化
        }
    }

    public void updatePlayerPosition(int row, int col) {
        if (playerX != -1 && playerY != -1) {
            rightRoom[playerX][playerY].setIcon(null);
        }
        playerX = row;
        playerY = col;
        rightRoom[row][col].setIcon(playerIcon);
        rightRoom[row][col].setDisabledIcon(playerIcon); // 無効化状態でも同じアイコンを表示
    }
}

    // メッセージ受信用のスレッドクラス
    private class MesgRecvThread extends Thread {
        Socket socket;
        String myName;

        public MesgRecvThread(Socket s, String n) {
            socket = s;
            myName = n;
        }

        public void run() {
            try {
                InputStreamReader sisr = new InputStreamReader(socket.getInputStream());
                BufferedReader br = new BufferedReader(sisr);
                out = new PrintWriter(socket.getOutputStream(), true);
                out.println(myName);

                while(true) {
                    String inputLine = br.readLine();
                    if (inputLine != null) {
                        System.out.println("受信: " + inputLine);
                        String[] inputTokens = inputLine.split(" ");
                        String cmd = inputTokens[0];

                        if(cmd.equals("CHAT")) {
                            String sender = inputTokens[1];
                            if (!sender.equals(myName)) {  // 自分のメッセージは表示しない
                                final String message = String.join(" ", Arrays.copyOfRange(inputTokens, 2, inputTokens.length));
                                SwingUtilities.invokeLater(() -> {
                                    if(gameScreen != null) {
                                        if (message.startsWith("MOVEMENT システム：")) {
                                            String formattedMessage = message.replace("MOVEMENT システム：", "相手: ");
                                            gameScreen.addChatMessage(formattedMessage);
                                        } else if (!message.startsWith("システム")) {
                                            gameScreen.addChatMessage("相手: " + message);
                                        }
                                    }
                                });
                            }
                        } else if(cmd.equals("READY")) {
                            String[] messageParts = inputLine.split(" ", 2);
                            if (messageParts.length < 2) {
                                System.out.println("無効なREADYメッセージ");
                                continue;
                            }
                            String sender = messageParts[1];
                            
                            if (!sender.equals(myName)) {
                                System.out.println("相手の準備完了");
                                System.out.println("相手の前に準備完了: " + opponentReady + ", isReady: " + isReady);
                                
                                if (!opponentReady) {
                                    opponentReady = true;
                                    System.out.println("相手の後に準備完了: " + opponentReady);
                                    
                                    if (isReady && gameScreen == null) {
                                        SwingUtilities.invokeLater(() -> {
                                            System.out.println("両プレイヤーが準備完了．ゲームスタート");
                                            startGame();
                                        });
                                    } else {
                                        System.out.println("準備完了を待っている");
                                    }
                                } else {
                                    System.out.println("相手はすでに準備完了");
                                }
                            } else {
                                System.out.println("自身のREADYメッセージ無視");
                            }
                        } else if(cmd.equals("MOVE")) {
                            String sender = inputTokens[1];
                            if (!sender.equals(myName)) {  // 自分の移動は無視
                                if (inputTokens.length < 3) {
                                    System.err.println("警告: 不正なMOVEメッセージ形式です");
                                    continue;
                                }
                                String position = inputTokens[2];
                                if (!position.matches("^[A-F][1-6]$")) {
                                    System.err.println("警告: 不正な移動位置形式です: " + position);
                                    continue;
                                }
                                final int row = position.charAt(0) - 'A';
                                final int col = position.charAt(1) - '1';
                                
                                // 座標の範囲チェック
                                if (row < 0 || row >= 6 || col < 0 || col >= 6) {
                                    System.err.println("警告: 座標が範囲外です - row: " + row + ", col: " + col);
                                    continue;
                                }

                                SwingUtilities.invokeLater(() -> {
                                    if(gameScreen != null) {
                                        gameScreen.updateOpponentPosition(row, col);
                                        gameScreen.addChatMessage("相手が " + position + " に移動しました");
                                    }
                                });
                            }
                        } else if(cmd.equals("TURN_ORDER")) {
                            String turnPlayer = inputTokens[1];
                            boolean isFirst = Boolean.parseBoolean(inputTokens[2]);
                            
                            SwingUtilities.invokeLater(() -> {
                                if(gameScreen != null) {
                                    if (turnPlayer.equals(myName)) {
                                        gameScreen.isMyTurn = isFirst;
                                        if (isFirst) {
                                            gameScreen.addChatMessage("システム: あなたがPlayer1（先行）です");
                                            gameScreen.addChatMessage("システム: Player1からゲームを開始してください");
                                        } else {
                                            gameScreen.addChatMessage("システム: あなたがPlayer2（後攻）です");
                                            gameScreen.addChatMessage("システム: Player1からゲームを開始します");
                                        }
                                        
                                        // 後攻の場合は入力を無効化
                                        if (!isFirst) {
                                            gameScreen.moveInput.setEnabled(false);
                                            gameScreen.sendButton.setEnabled(false);
                                        }
                                    }
                                }
                            });
                        } else if(cmd.equals("MAZE_INFO")) {
                            String sender = inputTokens[1];
                            if (!sender.equals(myName)) {  // 自分の迷路情報は無視
                                // 一時保存用の配列をクリア
                                tempOpponentWalls.clear();
                                tempOpponentGrid = new int[11][11];
                                
                                // グリッドの初期化
                                for(int i = 0; i < 11; i++) {
                                    for(int j = 0; j < 11; j++) {
                                        if (i%2!=0 && j%2!=0) {
                                            tempOpponentGrid[i][j] = 1;
                                        } else {
                                            tempOpponentGrid[i][j] = 0;
                                        }
                                    }
                                }

                                // 入口、出口、壁の情報を調べる
                                for(int i = 2; i < inputTokens.length; i++) {
                                    String info = inputTokens[i];
                                    if(info.startsWith("E")) { // 入口
                                        int pos = Integer.parseInt(info.substring(1));
                                        int row = pos / 6;
                                        int col = pos % 6;
                                        tempOpponentGrid[row*2][col*2] = 2;
                                    } else if(info.startsWith("X")) { // 出口
                                        int pos = Integer.parseInt(info.substring(1));
                                        int row = pos / 6;
                                        int col = pos % 6;
                                        tempOpponentGrid[row*2][col*2] = 3;
                                    } else if(info.startsWith("W")) { // 壁
                                        String[] coords = info.substring(1).split(",");
                                        int wallX = Integer.parseInt(coords[0]);
                                        int wallY = Integer.parseInt(coords[1]);
                                        tempOpponentWalls.add(new int[]{wallX, wallY});
                                        
                                        // グリッドに壁を設定
                                        if (wallX >= 100 && wallX < 200) { // 垂直の壁
                                            int row = (wallX - 100) / 5;
                                            int col = (wallX - 100) % 5;
                                            tempOpponentGrid[row*2][col*2+1] = 1;
                                        } else if (wallX >= 200) { // 水平の壁
                                            int row = (wallX - 200) / 6;
                                            int col = (wallX - 200) % 6;
                                            tempOpponentGrid[row*2+1][col*2] = 1;
                                        }
                                    }
                                }

                                // 情報が一時保存されたことを示すフラグを設定
                                hasTempMazeInfo = true;

                                // もしすでにゲーム画面が初期化されていれば、情報を直接設定（エラーよく出る）
                                if (gameScreen != null) {
                                    SwingUtilities.invokeLater(() -> {
                                        gameScreen.opponentWalls.clear();
                                        gameScreen.opponentWalls.addAll(tempOpponentWalls);
                                        gameScreen.opponentGrid = tempOpponentGrid.clone();
                                    });
                                }
                            }
                        } else if(cmd.equals("GOAL")) {
                            String goalSender = inputTokens[1];
                            if (!goalSender.equals(myName)) {  // 相手のゴール通知を受信
                                SwingUtilities.invokeLater(() -> {
                                    if(gameScreen != null) {
                                        gameScreen.gameEnded = true;
                                        JOptionPane.showMessageDialog(null, "相手プレイヤーがゴールに到達しました。", "ゲーム終了", JOptionPane.INFORMATION_MESSAGE);
                                        gameScreen.addChatMessage("システム: [ " + goalSender + "] がゴールに到達しました。あなたの負けです。");
                                        gameScreen.disableMovement();
                                    }
                                });
                            }
                        } else if(cmd.equals("WALL_HIT")) {
                            String wallHitSender = inputTokens[1];
                            if (!wallHitSender.equals(myName)) {  // 自分の壁判定は無視
                                SwingUtilities.invokeLater(() -> {
                                    if(gameScreen != null) {
                                        gameScreen.isMyTurn = true;  // 相手が壁に当たったので自分のターン
                                        gameScreen.addChatMessage("相手が壁に当たりました。あなたの番です");
                                        gameScreen.enableMovement();  // 入力を有効化
                                    }
                                });
                            } else {
                                // 自分が壁に当たった場合の処理（念のため）
                                if(gameScreen != null) {
                                    gameScreen.isMyTurn = false;  // 明示的に相手のターンに
                                    gameScreen.disableMovement();
                                }
                            }
                        }
                    } else {
                        break;
                    }
                }
                socket.close();
            } catch (IOException e) {
                System.err.println("エラーが発生しました: " + e);
            }
        }
    }
}

