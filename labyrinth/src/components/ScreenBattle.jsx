/**
 * プレイ画面コンポーネント
 * ゲーム進行中のメイン画面：プレイヤー移動、チャット、戦闘、目標管理など
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
    doc, getDoc, updateDoc, serverTimestamp, arrayUnion, arrayRemove,
    orderBy, limit, runTransaction, Timestamp, increment, collection, addDoc, query, onSnapshot
} from 'firebase/firestore';
import {
    ArrowUp, ArrowDown, ArrowLeft, ArrowRight, Eye, EyeOff, MessageSquare, Send, Users, User,
    CheckCircle, XCircle, Swords, RefreshCw, ListChecks,
    MinusCircle, PlusCircle, Award, Target, Clock, Users2, Handshake, Zap, Search, Move,
    Hourglass, ThumbsUp, ThumbsDown, Skull, MapPin, UserCheck, UserX, ShieldCheck, ShieldOff,
    Megaphone, MicOff, Trophy
} from 'lucide-react';

import { db, appId } from '../firebase';
import MazeGrid from './MazeGrid';
import BattleModal from './BattleModal';
import GameOverModal from './GameOverModal';
import {
    STANDARD_GRID_SIZE, NEGOTIATION_TYPES, SABOTAGE_TYPES,
    DECLARATION_PHASE_DURATION, CHAT_PHASE_DURATION, RESULT_PUBLICATION_DURATION, ACTION_EXECUTION_DELAY,
    PERSONAL_TIME_PENALTY_INTERVAL,
    PERSONAL_TIME_PENALTY_POINTS, DECLARATION_TIMEOUT_PENALTY, ALLIANCE_VIOLATION_PENALTY,
    SPECIAL_EVENT_INTERVAL_ROUNDS, SPECIAL_EVENTS // SECRET_OBJECTIVES, WALL_COUNT are used in other files
} from '../constants';
import { formatTime, isPathPossible } from '../utils';

const PlayScreen = ({ userId, setScreen, gameMode, debugMode }) => {
    const [gameId, setGameId] = useState(null);
    const [gameData, setGameData] = useState(null);
    const [myPlayerState, setMyPlayerState] = useState(null);
    const [mazeToPlayData, setMazeToPlayData] = useState(null);
    const [myCreatedMazeData, setMyCreatedMazeData] = useState(null);
    const [playerSolvingMyMaze, setPlayerSolvingMyMaze] = useState(null);
    const [message, setMessage] = useState("ゲーム開始！");
    const [showOpponentWallsDebug, setShowOpponentWallsDebug] = useState(false);
    const [chatMessages, setChatMessages] = useState([]);
    const [chatInput, setChatInput] = useState("");
    const chatLogRef = useRef(null);
    const [isBattleModalOpen, setIsBattleModalOpen] = useState(false);
    const [battleOpponentId, setBattleOpponentId] = useState("");
    const [gameType, setGameType] = useState('standard');
    const [phaseTimeLeft, setPhaseTimeLeft] = useState(null);
    const [overallTimeLeft, setOverallTimeLeft] = useState(null);
    const [selectedAction, setSelectedAction] = useState(null);
    const [actionTarget, setActionTarget] = useState(null);
    const [showActionDetails, setShowActionDetails] = useState(false);
    const [isPlacingTrap, setIsPlacingTrap] = useState(false);
    const [sharedWalls, setSharedWalls] = useState([]);
    const [sharedScoutLogs, setSharedScoutLogs] = useState([]);
    const personalTimerIntervalRef = useRef(null);
    const [isGameOverModalOpen, setIsGameOverModalOpen] = useState(false);
    // const [actionLogDisplay, setActionLogDisplay] = useState([]); // Using gameData.actionLog directly

    const [selectedMoveTarget, setSelectedMoveTarget] = useState(null);
    // デバッグモード用のプレイヤー切り替え機能
    const [debugCurrentPlayerId, setDebugCurrentPlayerId] = useState(userId);
    const [debugPlayerStates, setDebugPlayerStates] = useState({});
    const [debugMazeData, setDebugMazeData] = useState({});

    // 実際に使用するplayerStateとuserIdを決定
    const effectiveUserId = debugMode ? debugCurrentPlayerId : userId;
    const effectivePlayerState = debugMode ? debugPlayerStates[debugCurrentPlayerId] : myPlayerState;

    // 追加: 不足している変数の定義
    const isMyStandardTurn = gameData?.currentTurnPlayerId === effectiveUserId && gameType === 'standard';
    const inStandardBattleBetting = effectivePlayerState?.inBattleWith && gameType === 'standard';

    // 迷路データの読み込み
    useEffect(() => {
        if (!gameData || !effectivePlayerState) return;
        
        console.log("Loading maze data for game type:", gameType);
        console.log("Game data:", gameData);
        console.log("Effective player state:", effectivePlayerState);
        
        // 攻略する迷路の読み込み
        if (effectivePlayerState.assignedMazeOwnerId && gameData.mazes) {
            const assignedMaze = gameData.mazes[effectivePlayerState.assignedMazeOwnerId];
            if (assignedMaze) {
                console.log("Maze to play loaded:", assignedMaze);
                setMazeToPlayData(assignedMaze);
            } else {
                console.warn("Assigned maze not found for:", effectivePlayerState.assignedMazeOwnerId);
                setMessage(`割り当てられた迷路が見つかりません: ${effectivePlayerState.assignedMazeOwnerId}`);
            }
        }
        
        // 自分が作成した迷路の読み込み（スタンダードモードのみ）
        if (gameType === 'standard' && gameData.mazes?.[effectiveUserId]) {
            console.log("My created maze loaded:", gameData.mazes[effectiveUserId]);
            setMyCreatedMazeData(gameData.mazes[effectiveUserId]);
        }
        
    }, [gameData, effectivePlayerState, effectiveUserId, gameType, setMessage]);

    // デバッグモード時に全プレイヤーの状態を同期
    useEffect(() => {
        if (debugMode && gameData?.playerStates) {
            setDebugPlayerStates(gameData.playerStates);
            console.log("🔧 [DEBUG] Player states updated:", gameData.playerStates);
        }
    }, [debugMode, gameData?.playerStates]);

    // プレイヤー切り替え時に迷路データを更新
    useEffect(() => {
        if (debugMode && gameData?.mazes) {
            setDebugMazeData(gameData.mazes);
        }
    }, [debugMode, gameData?.mazes, debugCurrentPlayerId]);

    // Standard mode specific handlers
    const handleStandardMove = async (direction) => {
        // デバッグモード時は現在選択中のプレイヤーで移動、通常時は自分のターンのみ
        const canMove = debugMode ? true : (isMyStandardTurn && !inStandardBattleBetting);
        if (!canMove) return;
        
        const gameDocRef = doc(db, `artifacts/${appId}/public/data/labyrinthGames`, gameId);
        const { r: currentR, c: currentC } = effectivePlayerState.position;
        
        let newR = currentR;
        let newC = currentC;
        
        switch(direction) {
            case 'up': newR--; break;
            case 'down': newR++; break;
            case 'left': newC--; break;
            case 'right': newC++; break;
            default: return;
        }
        
        const gridSize = mazeToPlayData?.gridSize || STANDARD_GRID_SIZE;
        
        // 境界チェック
        if (newR < 0 || newR >= gridSize || newC < 0 || newC >= gridSize) {
            setMessage("盤外への移動はできません。");
            return;
        }
        
        // 壁チェック - 実際の迷路の壁構造をチェック
        const walls = mazeToPlayData?.walls || [];
        const isBlocked = walls.some(wall => {
            if (wall.type === 'horizontal') {
                // 水平壁：上下移動をブロック
                if (direction === 'up' && wall.r === currentR && wall.c === currentC) return true;
                if (direction === 'down' && wall.r === newR && wall.c === newC) return true;
            } else if (wall.type === 'vertical') {
                // 垂直壁：左右移動をブロック
                if (direction === 'left' && wall.r === currentR && wall.c === currentR) return true;
                if (direction === 'right' && wall.r === currentR && wall.c === newC) return true;
            }
            return false;
        });
        
        if (isBlocked) {
            setMessage("壁に阻まれて移動できません。");
            return;
        }
        
        try {
            const updates = {
                [`playerStates.${effectiveUserId}.position`]: { r: newR, c: newC },
                [`playerStates.${effectiveUserId}.lastMoveTime`]: serverTimestamp(),
            };
            
            // 新しいセルの発見ボーナス
            if (!effectivePlayerState.revealedCells[`${newR}-${newC}`]) {
                updates[`playerStates.${effectiveUserId}.score`] = increment(1);
                updates[`playerStates.${effectiveUserId}.revealedCells.${newR}-${newC}`] = true;
                setMessage(`(${newR},${newC})に移動！ +1pt`);
            } else {
                setMessage(`(${newR},${newC})に移動しました。`);
            }
            
            // ゴール判定
            if (mazeToPlayData && newR === mazeToPlayData.goal.r && newC === mazeToPlayData.goal.c && !effectivePlayerState.goalTime) {
                updates[`playerStates.${effectiveUserId}.goalTime`] = serverTimestamp();
                updates.goalCount = increment(1);
                setMessage("ゴール達成！");
            }
            
            // デバッグモード時は自動的にターン切り替え
            if (debugMode && gameData?.turnOrder) {
                const currentTurnIndex = gameData.turnOrder.indexOf(gameData.currentTurnPlayerId);
                const nextTurnIndex = (currentTurnIndex + 1) % gameData.turnOrder.length;
                const nextPlayerId = gameData.turnOrder[nextTurnIndex];
                
                updates.currentTurnPlayerId = nextPlayerId;
                updates.turnNumber = increment(1);
                
                console.log(`🔧 [DEBUG] Auto turn switch: ${gameData.currentTurnPlayerId.substring(0,8)}... → ${nextPlayerId.substring(0,8)}...`);
            }
            
            await updateDoc(gameDocRef, updates);
            
        } catch (error) {
            console.error("Error moving:", error);
            setMessage("移動に失敗しました。");
        }
    };

    const handleStandardBattleBet = async (betAmount) => {
        // スタンダードモードのバトル処理
        console.log("Battle bet:", betAmount);
    };

    // handleTrapCoordinateSelect関数の追加
    const handleTrapCoordinateSelect = (r, c) => {
        if (isPlacingTrap && selectedAction === 'sabotage' && sabotageType === 'trap') {
            setTrapPlacementCoord({ r, c });
            setIsPlacingTrap(false);
            setMessage(`トラップ設置座標 (${r}, ${c}) を選択しました。`);
        }
    };

    // セルクリック時の処理を統合
    const handleCellClick = (r, c) => {
        if (gameType === 'standard') {
            // スタンダードモード時の移動処理
            const canMove = debugMode ? true : (isMyStandardTurn && !inStandardBattleBetting);
            if (canMove) {
                const { r: currentR, c: currentC } = effectivePlayerState.position;
                const isAdjacent = (Math.abs(r - currentR) === 1 && c === currentC) || 
                                  (Math.abs(c - currentC) === 1 && r === currentR);
                
                if (isAdjacent) {
                    if (r < currentR) handleStandardMove('up');
                    else if (r > currentR) handleStandardMove('down');
                    else if (c < currentC) handleStandardMove('left');
                    else if (c > currentC) handleStandardMove('right');
                } else {
                    setMessage("隣接するセルにのみ移動できます。");
                }
            }
        }
    };

    // キーボード操作の追加
    useEffect(() => {
        const handleKeyPress = (event) => {
            if (gameType === 'standard' && isMyStandardTurn && !inStandardBattleBetting) {
                switch(event.key) {
                    case 'ArrowUp': 
                    case 'w': 
                    case 'W':
                        event.preventDefault();
                        handleStandardMove('up');
                        break;
                    case 'ArrowDown': 
                    case 's': 
                    case 'S':
                        event.preventDefault();
                        handleStandardMove('down');
                        break;
                    case 'ArrowLeft': 
                    case 'a': 
                    case 'A':
                        event.preventDefault();
                        handleStandardMove('left');
                        break;
                    case 'ArrowRight': 
                    case 'd': 
                    case 'D':
                        event.preventDefault();
                        handleStandardMove('right');
                        break;
                }
            }
        };

        window.addEventListener('keydown', handleKeyPress);
        return () => window.removeEventListener('keydown', handleKeyPress);
    }, [gameType, isMyStandardTurn, inStandardBattleBetting, handleStandardMove]);

    // ゲームデータを読み込む useEffect を修正
    useEffect(() => {
        if (!gameId) {
            const savedGameId = localStorage.getItem('labyrinthGameId');
            const savedGameType = localStorage.getItem('labyrinthGameType');
            if (savedGameId && savedGameType) {
                setGameId(savedGameId);
                setGameType(savedGameType);
                return;
            } else {
                setScreen('lobby');
                return;
            }
        }

        const gameDocRef = doc(db, `artifacts/${appId}/public/data/labyrinthGames`, gameId);
        const unsubscribe = onSnapshot(gameDocRef,
            (docSnap) => {
                if (docSnap.exists()) {
                    const data = docSnap.data();
                    console.log("Game data loaded:", data);
                    setGameData(data);
                    
                    const myState = data.playerStates?.[userId];
                    console.log("My player state:", myState);
                    setMyPlayerState(myState);
                    
                    // デバッグモード時は全プレイヤーの状態を保存
                    if (debugMode && data.playerStates) {
                        setDebugPlayerStates(data.playerStates);
                        console.log("🔧 [DEBUG] All player states updated:", data.playerStates);
                    }
                    
                    if (data.status === 'finished') {
                        setIsGameOverModalOpen(true);
                        return;
                    }
                    
                    // 迷路データの読み込みを修正
                    if (myState?.assignedMazeOwnerId && data.mazes) {
                        console.log("Assigned maze owner:", myState.assignedMazeOwnerId);
                        console.log("Available mazes:", Object.keys(data.mazes));
                        
                        const assignedMaze = data.mazes[myState.assignedMazeOwnerId];
                        if (assignedMaze) {
                            console.log("Maze to play loaded:", assignedMaze);
                            setMazeToPlayData(assignedMaze);
                        } else {
                            console.warn("Assigned maze not found for:", myState.assignedMazeOwnerId);
                            setMessage(`割り当てられた迷路が見つかりません: ${myState.assignedMazeOwnerId}`);
                        }
                    }
                    
                    // 自分が作成した迷路の読み込み
                    if (data.mazes?.[userId]) {
                        console.log("My created maze loaded:", data.mazes[userId]);
                        setMyCreatedMazeData(data.mazes[userId]);
                        
                        // 自分の迷路を攻略している相手プレイヤーを探す
                        const challenger = Object.entries(data.playerStates || {})
                            .find(([pid, ps]) => ps.assignedMazeOwnerId === userId && pid !== userId);
                        
                        if (challenger) {
                            setPlayerSolvingMyMaze({ id: challenger[0], ...challenger[1] });
                            console.log("Player solving my maze:", challenger[0]);
                        } else {
                            setPlayerSolvingMyMaze(null);
                        }
                    } else {
                        console.warn("My created maze not found for userId:", userId);
                    }
                } else {
                    console.error("Game document does not exist");
                    setMessage("ゲームが見つかりません。ロビーに戻ります。");
                    setTimeout(() => setScreen('lobby'), 3000);
                }
            },
            (error) => {
                console.error("Error loading game data:", error);
                setMessage("ゲームデータの読み込みに失敗しました。ロビーに戻ります。");
                setTimeout(() => setScreen('lobby'), 3000);
            }
        );
        
        return () => unsubscribe();
    }, [gameId, userId, setScreen]);

    // handleCellClickForMove関数の追加
    const handleCellClickForMove = (r, c) => {
        if (isSelectingMoveTarget && selectedAction === 'move') {
            // 現在位置からの移動可能性をチェック（隣接セルかどうか）
            const { r: currentR, c: currentC } = myPlayerState.position;
            const isAdjacent = (Math.abs(r - currentR) === 1 && c === currentC) || 
                              (Math.abs(c - currentC) === 1 && r === currentR);
            
            // グリッドサイズを適切に取得
            const gridSize = mazeToPlayData?.gridSize || currentGridSize;
            
            if (isAdjacent && r >= 0 && r < gridSize && c >= 0 && c < gridSize) {
                setSelectedMoveTarget({ r, c });
                setIsSelectingMoveTarget(false);
                setMessage(`移動先 (${r}, ${c}) を選択しました。`);
            } else {
                setMessage("隣接するセルにのみ移動できます。");
            }
        }
    };

    // チャットメッセージを読み込む useEffect を追加
    useEffect(() => {
        if (!gameId || !appId) return;
        
        const chatCollRef = collection(db, `artifacts/${appId}/public/data/labyrinthGames/${gameId}/chatMessages`);
        const chatQuery = query(chatCollRef, orderBy('timestamp', 'asc'), limit(50));
        
        const unsubscribe = onSnapshot(chatQuery, (snapshot) => {
            const messages = snapshot.docs.map(doc => ({
                id: doc.id,
                ...doc.data()
            }));
            setChatMessages(messages);
        });
        
        return () => unsubscribe();
    }, [gameId, appId]);

    const sendSystemChatMessage = useCallback(async (text) => {
        if (!gameId) return;
        const chatCollRef = collection(db, `artifacts/${appId}/public/data/labyrinthGames/${gameId}/chatMessages`);
        try {
            await addDoc(chatCollRef, { senderId: "system", senderName: "システム", text: text, timestamp: serverTimestamp() });
        } catch (error) { console.error("Error sending system chat message:", error); }
    }, [gameId]);

    // 不足している関数の実装
    const handleStandardMoveImproved = async (direction) => {
        if (!isMyStandardTurn || inStandardBattleBetting) return;
        
        const gameDocRef = doc(db, `artifacts/${appId}/public/data/labyrinthGames`, gameId);
        const { r: currentR, c: currentC } = myPlayerState.position;
        
        let newR = currentR;
        let newC = currentC;
        
        switch(direction) {
            case 'up': newR--; break;
            case 'down': newR++; break;
            case 'left': newC--; break;
            case 'right': newC++; break;
            default: return;
        }
        
        const gridSize = mazeToPlayData?.gridSize || STANDARD_GRID_SIZE;
        
        // 境界チェック
        if (newR < 0 || newR >= gridSize || newC < 0 || newC >= gridSize) {
            setMessage("盤外への移動はできません。");
            return;
        }
        
        // 壁チェック - 実際の迷路の壁構造をチェック
        const walls = mazeToPlayData?.walls || [];
        const isBlocked = walls.some(wall => {
            if (wall.type === 'horizontal') {
                // 水平壁：上下移動をブロック
                if (direction === 'up' && wall.r === currentR && wall.c === currentC) return true;
                if (direction === 'down' && wall.r === newR && wall.c === newC) return true;
            } else if (wall.type === 'vertical') {
                // 垂直壁：左右移動をブロック
                if (direction === 'left' && wall.r === currentR && wall.c === currentR) return true;
                if (direction === 'right' && wall.r === currentR && wall.c === newC) return true;
            }
            return false;
        });
        
        if (isBlocked) {
            setMessage("壁に阻まれて移動できません。");
            return;
        }
        
        try {
            const updates = {
                [`playerStates.${userId}.position`]: { r: newR, c: newC },
                [`playerStates.${userId}.lastMoveTime`]: serverTimestamp(),
            };
            
            // 新しいセルの発見ボーナス
            if (!myPlayerState.revealedCells[`${newR}-${newC}`]) {
                updates[`playerStates.${userId}.score`] = increment(1);
                updates[`playerStates.${userId}.revealedCells.${newR}-${newC}`] = true;
                setMessage(`(${newR},${newC})に移動！ +1pt`);
            } else {
                setMessage(`(${newR},${newC})に移動しました。`);
            }
            
            // ゴール判定
            if (mazeToPlayData && newR === mazeToPlayData.goal.r && newC === mazeToPlayData.goal.c && !myPlayerState.goalTime) {
                updates[`playerStates.${userId}.goalTime`] = serverTimestamp();
                updates.goalCount = increment(1);
                setMessage("ゴール達成！");
            }
            
            await updateDoc(gameDocRef, updates);
            
            // スタンダードモード：移動後にターン進行
            setTimeout(() => {
                advanceStandardTurn();
            }, 1500);
            
        } catch (error) {
            console.error("Error moving:", error);
            setMessage("移動に失敗しました。");
        }
    };



//ここから4人モードの処理　
    if(freshData.players.length==4){
    // スタンダードモード専用：ターン進行の実装
    const advanceStandardTurn = useCallback(async () => {
//        if (gameType !== 'standard' || !gameData || !gameId) return;
        
        const gameDocRef = doc(db, `artifacts/${appId}/public/data/labyrinthGames`, gameId);
        
        try {
            await runTransaction(db, async (transaction) => {
                const freshGameSnap = await transaction.get(gameDocRef);
                if (!freshGameSnap.exists()) return;
                
                const freshData = freshGameSnap.data();
                const currentPlayerIndex = freshData.players.indexOf(freshData.currentTurnPlayerId);
                const nextPlayerIndex = (currentPlayerIndex + 1) % freshData.players.length;
                const nextPlayerId = freshData.players[nextPlayerIndex];
                
                const updates = {
                    currentTurnPlayerId: nextPlayerId,
                    turnNumber: increment(1)
                };
                
                // ゴール判定とゲーム終了チェック
                const goaledPlayers = freshData.players.filter(pid => 
                    freshData.playerStates[pid]?.goalTime
                );
                
                // 4人対戦の場合,ゴールしたプレイヤーが3人になればよい
                const playersToFinish = 3;

                if (goaledPlayers.length >= playersToFinish) {
                    updates.status = 'finished';
                    
                    // ランキング計算
                    const rankedPlayers = freshData.players.map(pid => ({
                        id: pid,
                        goalTime: freshData.playerStates[pid]?.goalTime?.toMillis() || Infinity,
                        score: freshData.playerStates[pid]?.score || 0
                    })).sort((a, b) => {
                        if (a.goalTime !== b.goalTime) return a.goalTime - b.goalTime;
                        return b.score - a.score;
                    });
                    
                    rankedPlayers.forEach((player, index) => {
                        updates[`playerStates.${player.id}.rank`] = index + 1;
                    });
                }
                
                transaction.update(gameDocRef, updates);
            });
            
        } catch (error) {
            console.error("Error advancing standard turn:", error);
        }
    }, [gameType, gameData, gameId]);

    // アクション実行フェーズでの自動実行
    useEffect(() => {

    }, [gameData?.currentActionPlayerId, myPlayerState?.actionExecutedThisTurn, executeMyDeclaredAction, gameType, userId]);

    // handleSendChatMessage関数の実装
    const handleSendChatMessage = async () => {
        if (!chatInput.trim() || !gameId) return;
        
        // 通信妨害チェック
        if (gameData?.specialEventActive?.type === 'communication_jam' ||
            myPlayerState?.sabotageEffects?.some(eff => eff.type === 'info_jam' && eff.expiryRound >= gameData?.roundNumber)) {
            setMessage("通信が妨害されています。");
            return;
        }
        
        const chatCollRef = collection(db, `artifacts/${appId}/public/data/labyrinthGames/${gameId}/chatMessages`);
        
        try {
            await addDoc(chatCollRef, {
                senderId: userId,
                senderName: userId.substring(0, 8) + "...",
                text: chatInput,
                timestamp: serverTimestamp()
            });
            setChatInput("");
        } catch (error) {
            console.error("Error sending chat message:", error);
            setMessage("メッセージ送信に失敗しました。");
        }
    };

    // ActionButtonコンポーネントを追加
    const ActionButton = ({ actionType, label, icon: Icon, currentSelection, onSelect }) => {
        const isSelected = currentSelection === actionType;
        return (
            <button
                onClick={() => {
                    onSelect(actionType);
                    setShowActionDetails(true);
                }}
                className={`p-2 rounded-lg border-2 text-sm transition-all duration-200 ${
                    isSelected 
                        ? 'border-blue-500 bg-blue-100 text-blue-800' 
                        : 'border-gray-300 bg-white hover:border-blue-300 hover:bg-blue-50'
                }`}
            >
                <div className="flex items-center justify-center space-x-1">
                    <Icon size={16}/>
                    <span>{label}</span>
                </div>
            </button>
        );
    };
    // デバッグモード用のプレイヤー切り替えコンポーネント
    const DebugPlayerSwitcher = () => {
        if (!debugMode || !gameData?.players) return null;
        return (
            <div className="bg-yellow-100 border-l-4 border-yellow-500 p-3 mb-4">
                <div className="flex items-center space-x-2">
                    <span className="text-yellow-800 font-semibold">🔧 DEBUG MODE:</span>
                    <span className="text-yellow-700">プレイヤー切り替え:</span>
                    <div className="flex space-x-1">
                        {gameData.players.map((playerId, index) => (
                            <button
                                key={playerId}
                                onClick={() => {
                                    setDebugCurrentPlayerId(playerId);
                                    console.log(`🔧 [DEBUG] Switched to player ${index + 1}: ${playerId.substring(0,8)}...`);
                                }}
                                className={`px-3 py-1 rounded text-sm font-medium ${
                                    debugCurrentPlayerId === playerId
                                        ? 'bg-blue-500 text-white'
                                        : 'bg-white text-gray-700 hover:bg-gray-100'
                                }`}
                            >
                                P{index + 1}
                            </button>
                        ))}
                    </div>
                    <span className="text-yellow-700 text-sm">
                        現在: {debugCurrentPlayerId?.substring(0,8)}...
                    </span>
                </div>
            </div>
        );
    };

    return (
        <div className="max-w-7xl mx-auto p-4 bg-gray-100 min-h-screen">
            {/* デバッグモード時のプレイヤー切り替えUI */}
            <DebugPlayerSwitcher />
            
            {/* ヘッダー部分を簡素化 */}
            <div className="bg-white rounded-lg shadow-md p-4 mb-4">
                <div className="flex justify-between items-center">
                    <h1 className="text-2xl font-bold text-gray-800">
                        {debugMode && <span className="text-yellow-600 ml-2 text-lg">🔧 DEBUG</span>}
                    </h1>
                    <button
                        onClick={() => setScreen('lobby')}
                        className="bg-red-500 hover:bg-red-600 text-white px-4 py-2 rounded"
                    >
                        ロビーに戻る
                    </button>
                </div>
                
                {/* メッセージエリアのみ残す */}
                {message && (
                    <div className="mt-4 p-3 bg-yellow-50 rounded border-l-4 border-yellow-400">
                        <p className="text-yellow-800 text-sm">{message}</p>
                    </div>
                )}
            </div>

            {/* メインコンテンツ：スタンダードモード */}
            {gameType === 'standard' ? (
                // スタンダードモード（四人対戦）レイアウト
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                    {/* 左：自分が設定した迷宮 */}
                    <div className="bg-white rounded-lg shadow-md p-4">
                        <h2 className="text-lg font-semibold mb-4">
                            あなたの設定した迷宮
                        </h2>
                        
                        {myCreatedMazeData ? (
                            <div>
                                {/* スタンダードモード - 自分が設定した迷宮 */}
                                <MazeGrid
                                    mazeData={myCreatedMazeData}
                                    playerPosition={playerSolvingMyMaze?.position}
                                    otherPlayers={playerSolvingMyMaze ? [playerSolvingMyMaze] : []}
                                    showAllWalls={true}
                                    onCellClick={() => {}}
                                    gridSize={currentGridSize}
                                    sharedWalls={[]}
                                    highlightPlayer={false}
                                    smallView={false}
                                />
                                {playerSolvingMyMaze && (
                                    <div className="mt-3 p-2 bg-gray-50 rounded text-sm">
                                        <p className="font-semibold text-gray-700">攻略者の状態:</p>
                                        <p>位置: ({playerSolvingMyMaze.position?.r || 0}, {playerSolvingMyMaze.position?.c || 0})</p>
                                        <p>スコア: {playerSolvingMyMaze.score || 0}pt</p>
                                        {playerSolvingMyMaze.goalTime && (
                                            <p className="text-green-600 font-semibold">ゴール達成！</p>
                                        )}
                                    </div>
                                )}
                            </div>
                        ) : (
                            <div className="flex items-center justify-center h-64 bg-gray-50 rounded">
                                <div className="text-center">
                                    <p className="text-gray-500 mb-2">迷宮データを読み込み中...</p>
                                    <p className="text-xs text-gray-400">ゲームID: {gameId}</p>
                                    <p className="text-xs text-gray-400">ユーザーID: {userId}</p>
                                </div>
                            </div>
                        )}
                    </div>

                    {/* 中央：自分が攻略する迷宮 */}
                    <div className="bg-white rounded-lg shadow-md p-4">
                        <h2 className="text-lg font-semibold mb-4">
                            攻略する迷宮
                        </h2>
                        
                        {/* 現在のターン表示 */}
                        <div className="mb-4 p-3 bg-blue-50 rounded-lg">
                            <div className="flex items-center justify-between">
                                <div>
                                    <h4 className="font-semibold text-blue-700">現在のターン</h4>
                                    <p className="text-sm text-blue-600">
                                        {gameData?.currentTurnPlayerId === effectiveUserId ? 
                                            <span className="font-bold text-green-600">あなた</span> : 
                                            <span className="font-bold text-orange-600">相手</span>
                                        } (ターン数: {gameData?.turnNumber || 1})
                                    </p>
                                </div>
                                <div className="text-right text-sm">
                                    <p className="text-blue-700">
                                        {debugMode ? `プレイヤー ${effectiveUserId.substring(0,8)}...` : 'あなた'}の状態
                                    </p>
                                    <p className="text-blue-600">
                                        位置: ({effectivePlayerState?.position?.r || 0}, {effectivePlayerState?.position?.c || 0})
                                        <br />
                                        スコア: {effectivePlayerState?.score || 0}pt
                                    </p>
                                </div>
                            </div>
                        </div>
                        
                        {/* 移動方法説明 */}
                        {isMyStandardTurn && (
                            <div className="mb-4 p-3 bg-green-50 rounded-lg">
                                <h4 className="font-semibold text-green-700 mb-2">🎮 移動方法</h4>
                                <div className="text-sm text-green-600 space-y-1">
                                    <p><strong>方法1:</strong> 右下の移動宣言ボタンを使用</p>
                                    <p><strong>方法2:</strong> 迷路上の隣接するセルを直接クリック</p>
                                    <p><strong>方法3:</strong> キーボードの矢印キー または WASD</p>
                                </div>
                            </div>
                        )}

                        {/* 迷路グリッド */}
                        {mazeToPlayData ? (
                            <MazeGrid
                                mazeData={mazeToPlayData}
                                playerPosition={effectivePlayerState?.position}
                                otherPlayers={gameData?.playerStates ? 
                                    Object.entries(gameData.playerStates)
                                        .filter(([pid]) => pid !== effectiveUserId)
                                        .map(([pid, pState]) => ({ id: pid, position: pState.position })) 
                                    : []
                                }
                                revealedCells={effectivePlayerState?.revealedCells || {}}
                                revealedPlayerWalls={effectivePlayerState?.revealedWalls || []}
                                onCellClick={handleCellClick}
                                gridSize={currentGridSize}
                                sharedWalls={sharedWalls}
                                highlightPlayer={true}
                                smallView={false}
                            />
                        ) : (
                            <div className="flex items-center justify-center h-64 bg-gray-50 rounded">
                                <div className="text-center">
                                    <p className="text-gray-500 mb-2">攻略迷路を読み込み中...</p>
                                    <p className="text-xs text-gray-400">割り当てられた迷路作成者: {myPlayerState?.assignedMazeOwnerId || "未割り当て"}</p>
                                    {gameData?.mazes && (
                                        <p className="text-xs text-gray-400 mt-2">
                                            利用可能な迷路: {Object.keys(gameData.mazes).join(", ") || "なし"}
                                        </p>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>

                    {/* 右：チャット＆移動宣言 */}
                    <div className="space-y-4">
                        {/* 上部：チャットエリア */}
                        <div className="bg-white rounded-lg shadow-md p-4">
                            <h4 className="text-lg font-semibold mb-3 flex items-center">
                                <MessageSquare size={18} className="mr-2"/> チャット
                            </h4>
                            <div ref={chatLogRef} className="bg-gray-50 p-3 rounded-lg h-40 overflow-y-auto text-sm mb-3 border">
                                {chatMessages.map(msg => (
                                    <div key={msg.id} className={`mb-2 ${msg.senderId === 'system' ? 'text-blue-600 font-semibold' : ''}`}>
                                        <span className="font-medium">{msg.senderName}:</span> {msg.text}
                                    </div>
                                ))}
                            </div>
                            <div className="flex space-x-2">
                                <input 
                                    type="text" 
                                    value={chatInput} 
                                    onChange={(e) => setChatInput(e.target.value)}
                                    onKeyPress={(e) => e.key === 'Enter' && handleSendChatMessage()}
                                    className="flex-1 px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    placeholder="メッセージを入力..."
                                />
                                <button 
                                    onClick={() => handleSendChatMessage()}
                                    className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg transition-colors"
                                    disabled={!chatInput.trim()}
                                >
                                    <Send size={16}/>
                                </button>
                            </div>
                        </div>

                        {/* 下部：移動宣言エリア */}
                        <div className="bg-white rounded-lg shadow-md p-4">
                            <h4 className="text-lg font-semibold mb-3">移動宣言</h4>
                            
                            {isMyStandardTurn && !inStandardBattleBetting ? (
                                <div className="space-y-3">
                                    {/* ターン状態表示 */}
                                    <div className="p-3 bg-green-50 rounded-lg text-center">
                                        <p className="text-green-600 font-semibold">🟢 あなたのターン</p>
                                        <p className="text-sm text-green-500">移動を選択してください</p>
                                    </div>
                                    
                                    {/* 方向ボタン */}
                                    <div className="grid grid-cols-3 gap-2 max-w-48 mx-auto">
                                        <div></div>
                                        <button 
                                            onClick={() => handleStandardMoveImproved('up')}
                                            className="bg-blue-500 hover:bg-blue-600 text-white p-3 rounded-lg flex items-center justify-center transition-colors shadow-md"
                                            title="上に移動 (W キー)"
                                        >
                                            <ArrowUp size={20}/>
                                        </button>
                                        <div></div>
                                        
                                        <button 
                                            onClick={() => handleStandardMoveImproved('left')}
                                            className="bg-blue-500 hover:bg-blue-600 text-white p-3 rounded-lg flex items-center justify-center transition-colors shadow-md"
                                            title="左に移動 (A キー)"
                                        >
                                            <ArrowLeft size={20}/>
                                        </button>
                                        <div className="bg-gray-200 rounded-lg p-3 flex items-center justify-center">
                                            <User size={20} className="text-gray-500"/>
                                        </div>
                                        <button 
                                            onClick={() => handleStandardMoveImproved('right')}
                                            className="bg-blue-500 hover:bg-blue-600 text-white p-3 rounded-lg flex items-center justify-center transition-colors shadow-md"
                                            title="右に移動 (D キー)"
                                        >
                                            <ArrowRight size={20}/>
                                        </button>
                                        
                                        <div></div>
                                        <button 
                                            onClick={() => handleStandardMoveImproved('down')}
                                            className="bg-blue-500 hover:bg-blue-600 text-white p-3 rounded-lg flex items-center justify-center transition-colors shadow-md"
                                            title="下に移動 (S キー)"
                                        >
                                            <ArrowDown size={20}/>
                                        </button>
                                        <div></div>
                                    </div>
                                    
                                    {/* キーボードヒント */}
                                    <div className="text-center text-xs text-gray-500 bg-gray-50 p-2 rounded">
                                        💡 キーボード: ↑↓←→ または WASD でも移動可能
                                    </div>
                                    
                                    {/* プレイヤー情報 */}
                                    <div className="pt-3 border-t">
                                        <h5 className="font-semibold mb-2 text-sm">プレイヤー状況</h5>
                                        <div className="space-y-2">
                                            {gameData?.players?.map(playerId => {
                                                const player = gameData.playerStates[playerId];
                                                const isCurrentPlayer = playerId === userId;
                                                const isActivePlayer = gameData.currentTurnPlayerId === playerId;
                                                
                                                return (
                                                    <div 
                                                        key={playerId}
                                                        className={`p-2 rounded border text-sm ${
                                                            isCurrentPlayer ? 'border-blue-300 bg-blue-50' : 'border-gray-200 bg-gray-50'
                                                        } ${isActivePlayer ? 'ring-2 ring-green-300' : ''}`}
                                                    >
                                                        <div className="flex items-center justify-between">
                                                            <div className="flex items-center space-x-1">
                                                                <User size={14} className={isCurrentPlayer ? 'text-blue-600' : 'text-gray-500'}/>
                                                                <span className={`font-medium ${isCurrentPlayer ? 'text-blue-800' : 'text-gray-700'}`}>
                                                                    {isCurrentPlayer ? 'あなた' : '相手'}
                                                                </span>
                                                                {isActivePlayer && (
                                                                    <span className="bg-green-500 text-white text-xs px-1 py-0.5 rounded">
                                                                        ターン中
                                                                    </span>
                                                                )}
                                                            </div>
                                                            <div className="text-right text-xs">
                                                                <div>スコア: {player?.score || 0}pt</div>
                                                                <div className="text-gray-500">
                                                                    位置: ({player?.position?.r || 0}, {player?.position?.c || 0})
                                                                </div>
                                                            </div>
                                                        </div>
                                                        
                                                        {/* ゴール状態表示 */}
                                                        {player?.goalTime && (
                                                            <div className="mt-1 flex items-center space-x-1">
                                                                <Trophy size={12} className="text-yellow-500"/>
                                                                <span className="text-xs text-yellow-600 font-semibold">ゴール達成！</span>
                                                            </div>
                                                        )}
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                </div>
                            ) : inStandardBattleBetting ? (
                                <div className="text-center p-4 bg-red-50 rounded-lg">
                                    <Swords className="mx-auto mb-2 text-red-600" size={24}/>
                                    <p className="text-red-600 font-semibold">バトル中</p>
                                    <p className="text-sm text-red-500">移動はできません</p>
                                </div>
                            ) : (
                                <div className="text-center p-4 bg-gray-50 rounded-lg">
                                    <Clock className="mx-auto mb-2 text-gray-500" size={24}/>
                                    <p className="text-gray-600 font-semibold">相手のターン</p>
                                    <p className="text-sm text-gray-500">相手の移動を待っています...</p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
        ) : null}

            {/* モーダル */}
            {isBattleModalOpen && (
                <BattleModal
                    isOpen={isBattleModalOpen}
                    onClose={() => setIsBattleModalOpen(false)}
                    gameData={gameData}
                    userId={userId}
                    opponentId={battleOpponentId}
                    onBet={handleStandardBattleBet}
                />
            )}

            {isGameOverModalOpen && (
                <GameOverModal
                    isOpen={isGameOverModalOpen}
                    onClose={() => setIsGameOverModalOpen(false)}
                    gameData={gameData}
                    userId={userId}
                    onReturnToLobby={() => setScreen('lobby')}
                />
            )}
        </div>
    );
}
};


//ここから2人モードの処理　
    if(freshData.players.length==2){
    // スタンダードモード専用：ターン進行の実装
    const advanceStandardTurn = useCallback(async () => {
//        if (gameType !== 'standard' || !gameData || !gameId) return;
        
        const gameDocRef = doc(db, `artifacts/${appId}/public/data/labyrinthGames`, gameId);
        
        try {
            await runTransaction(db, async (transaction) => {
                const freshGameSnap = await transaction.get(gameDocRef);
                if (!freshGameSnap.exists()) return;
                
                const freshData = freshGameSnap.data();
                const currentPlayerIndex = freshData.players.indexOf(freshData.currentTurnPlayerId);
                const nextPlayerIndex = (currentPlayerIndex + 1) % freshData.players.length;
                const nextPlayerId = freshData.players[nextPlayerIndex];
                
                const updates = {
                    currentTurnPlayerId: nextPlayerId,
                    turnNumber: increment(1)
                };
                
                // ゴール判定とゲーム終了チェック
                const goaledPlayers = freshData.players.filter(pid => 
                    freshData.playerStates[pid]?.goalTime
                );
                
                // 2人プレイの場合、1人がゴールしたら終了
                const playersToFinish = 1;

                if (goaledPlayers.length >= playersToFinish) {
                    updates.status = 'finished';
                    
                    // ランキング計算
                    const rankedPlayers = freshData.players.map(pid => ({
                        id: pid,
                        goalTime: freshData.playerStates[pid]?.goalTime?.toMillis() || Infinity,
                        score: freshData.playerStates[pid]?.score || 0
                    })).sort((a, b) => {
                        if (a.goalTime !== b.goalTime) return a.goalTime - b.goalTime;
                        return b.score - a.score;
                    });
                    
                    rankedPlayers.forEach((player, index) => {
                        updates[`playerStates.${player.id}.rank`] = index + 1;
                    });
                }
                
                transaction.update(gameDocRef, updates);
            });
            
        } catch (error) {
            console.error("Error advancing standard turn:", error);
        }
    }, [gameType, gameData, gameId]);

    // アクション実行フェーズでの自動実行
    useEffect(() => {

    }, [gameData?.currentActionPlayerId, myPlayerState?.actionExecutedThisTurn, executeMyDeclaredAction, gameType, userId]);

    // handleSendChatMessage関数の実装
    const handleSendChatMessage = async () => {
        if (!chatInput.trim() || !gameId) return;
        
        // 通信妨害チェック
        if (gameData?.specialEventActive?.type === 'communication_jam' ||
            myPlayerState?.sabotageEffects?.some(eff => eff.type === 'info_jam' && eff.expiryRound >= gameData?.roundNumber)) {
            setMessage("通信が妨害されています。");
            return;
        }
        
        const chatCollRef = collection(db, `artifacts/${appId}/public/data/labyrinthGames/${gameId}/chatMessages`);
        
        try {
            await addDoc(chatCollRef, {
                senderId: userId,
                senderName: userId.substring(0, 8) + "...",
                text: chatInput,
                timestamp: serverTimestamp()
            });
            setChatInput("");
        } catch (error) {
            console.error("Error sending chat message:", error);
            setMessage("メッセージ送信に失敗しました。");
        }
    };

    // ActionButtonコンポーネントを追加
    const ActionButton = ({ actionType, label, icon: Icon, currentSelection, onSelect }) => {
        const isSelected = currentSelection === actionType;
        return (
            <button
                onClick={() => {
                    onSelect(actionType);
                    setShowActionDetails(true);
                }}
                className={`p-2 rounded-lg border-2 text-sm transition-all duration-200 ${
                    isSelected 
                        ? 'border-blue-500 bg-blue-100 text-blue-800' 
                        : 'border-gray-300 bg-white hover:border-blue-300 hover:bg-blue-50'
                }`}
            >
                <div className="flex items-center justify-center space-x-1">
                    <Icon size={16}/>
                    <span>{label}</span>
                </div>
            </button>
        );
    };
    // デバッグモード用のプレイヤー切り替えコンポーネント
    const DebugPlayerSwitcher = () => {
        if (!debugMode || !gameData?.players) return null;
        return (
            <div className="bg-yellow-100 border-l-4 border-yellow-500 p-3 mb-4">
                <div className="flex items-center space-x-2">
                    <span className="text-yellow-800 font-semibold">🔧 DEBUG MODE:</span>
                    <span className="text-yellow-700">プレイヤー切り替え:</span>
                    <div className="flex space-x-1">
                        {gameData.players.map((playerId, index) => (
                            <button
                                key={playerId}
                                onClick={() => {
                                    setDebugCurrentPlayerId(playerId);
                                    console.log(`🔧 [DEBUG] Switched to player ${index + 1}: ${playerId.substring(0,8)}...`);
                                }}
                                className={`px-3 py-1 rounded text-sm font-medium ${
                                    debugCurrentPlayerId === playerId
                                        ? 'bg-blue-500 text-white'
                                        : 'bg-white text-gray-700 hover:bg-gray-100'
                                }`}
                            >
                                P{index + 1}
                            </button>
                        ))}
                    </div>
                    <span className="text-yellow-700 text-sm">
                        現在: {debugCurrentPlayerId?.substring(0,8)}...
                    </span>
                </div>
            </div>
        );
    };

    return (
        <div className="max-w-7xl mx-auto p-4 bg-gray-100 min-h-screen">
            {/* デバッグモード時のプレイヤー切り替えUI */}
            <DebugPlayerSwitcher />
            
            {/* ヘッダー部分を簡素化 */}
            <div className="bg-white rounded-lg shadow-md p-4 mb-4">
                <div className="flex justify-between items-center">
                    <h1 className="text-2xl font-bold text-gray-800">
                        {debugMode && <span className="text-yellow-600 ml-2 text-lg">🔧 DEBUG</span>}
                    </h1>
                    <button
                        onClick={() => setScreen('lobby')}
                        className="bg-red-500 hover:bg-red-600 text-white px-4 py-2 rounded"
                    >
                        ロビーに戻る
                    </button>
                </div>
                
                {/* メッセージエリアのみ残す */}
                {message && (
                    <div className="mt-4 p-3 bg-yellow-50 rounded border-l-4 border-yellow-400">
                        <p className="text-yellow-800 text-sm">{message}</p>
                    </div>
                )}
            </div>

            {/* メインコンテンツ：スタンダードモード */}
            {gameType === 'standard' ? (
                // スタンダードモード（二人対戦）レイアウト
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                    {/* 左：自分が設定した迷宮 */}
                    <div className="bg-white rounded-lg shadow-md p-4">
                        <h2 className="text-lg font-semibold mb-4">
                            あなたの設定した迷宮
                        </h2>
                        
                        {myCreatedMazeData ? (
                            <div>
                                {/* スタンダードモード - 自分が設定した迷宮 */}
                                <MazeGrid
                                    mazeData={myCreatedMazeData}
                                    playerPosition={playerSolvingMyMaze?.position}
                                    otherPlayers={playerSolvingMyMaze ? [playerSolvingMyMaze] : []}
                                    showAllWalls={true}
                                    onCellClick={() => {}}
                                    gridSize={currentGridSize}
                                    sharedWalls={[]}
                                    highlightPlayer={false}
                                    smallView={false}
                                />
                                {playerSolvingMyMaze && (
                                    <div className="mt-3 p-2 bg-gray-50 rounded text-sm">
                                        <p className="font-semibold text-gray-700">攻略者の状態:</p>
                                        <p>位置: ({playerSolvingMyMaze.position?.r || 0}, {playerSolvingMyMaze.position?.c || 0})</p>
                                        <p>スコア: {playerSolvingMyMaze.score || 0}pt</p>
                                        {playerSolvingMyMaze.goalTime && (
                                            <p className="text-green-600 font-semibold">ゴール達成！</p>
                                        )}
                                    </div>
                                )}
                            </div>
                        ) : (
                            <div className="flex items-center justify-center h-64 bg-gray-50 rounded">
                                <div className="text-center">
                                    <p className="text-gray-500 mb-2">迷宮データを読み込み中...</p>
                                    <p className="text-xs text-gray-400">ゲームID: {gameId}</p>
                                    <p className="text-xs text-gray-400">ユーザーID: {userId}</p>
                                </div>
                            </div>
                        )}
                    </div>

                    {/* 中央：自分が攻略する迷宮 */}
                    <div className="bg-white rounded-lg shadow-md p-4">
                        <h2 className="text-lg font-semibold mb-4">
                            攻略する迷宮
                        </h2>
                        
                        {/* 現在のターン表示 */}
                        <div className="mb-4 p-3 bg-blue-50 rounded-lg">
                            <div className="flex items-center justify-between">
                                <div>
                                    <h4 className="font-semibold text-blue-700">現在のターン</h4>
                                    <p className="text-sm text-blue-600">
                                        {gameData?.currentTurnPlayerId === effectiveUserId ? 
                                            <span className="font-bold text-green-600">あなた</span> : 
                                            <span className="font-bold text-orange-600">相手</span>
                                        } (ターン数: {gameData?.turnNumber || 1})
                                    </p>
                                </div>
                                <div className="text-right text-sm">
                                    <p className="text-blue-700">
                                        {debugMode ? `プレイヤー ${effectiveUserId.substring(0,8)}...` : 'あなた'}の状態
                                    </p>
                                    <p className="text-blue-600">
                                        位置: ({effectivePlayerState?.position?.r || 0}, {effectivePlayerState?.position?.c || 0})
                                        <br />
                                        スコア: {effectivePlayerState?.score || 0}pt
                                    </p>
                                </div>
                            </div>
                        </div>
                        
                        {/* 移動方法説明 */}
                        {isMyStandardTurn && (
                            <div className="mb-4 p-3 bg-green-50 rounded-lg">
                                <h4 className="font-semibold text-green-700 mb-2">🎮 移動方法</h4>
                                <div className="text-sm text-green-600 space-y-1">
                                    <p><strong>方法1:</strong> 右下の移動宣言ボタンを使用</p>
                                    <p><strong>方法2:</strong> 迷路上の隣接するセルを直接クリック</p>
                                    <p><strong>方法3:</strong> キーボードの矢印キー または WASD</p>
                                </div>
                            </div>
                        )}

                        {/* 迷路グリッド */}
                        {mazeToPlayData ? (
                            <MazeGrid
                                mazeData={mazeToPlayData}
                                playerPosition={effectivePlayerState?.position}
                                otherPlayers={gameData?.playerStates ? 
                                    Object.entries(gameData.playerStates)
                                        .filter(([pid]) => pid !== effectiveUserId)
                                        .map(([pid, pState]) => ({ id: pid, position: pState.position })) 
                                    : []
                                }
                                revealedCells={effectivePlayerState?.revealedCells || {}}
                                revealedPlayerWalls={effectivePlayerState?.revealedWalls || []}
                                onCellClick={handleCellClick}
                                gridSize={currentGridSize}
                                sharedWalls={sharedWalls}
                                highlightPlayer={true}
                                smallView={false}
                            />
                        ) : (
                            <div className="flex items-center justify-center h-64 bg-gray-50 rounded">
                                <div className="text-center">
                                    <p className="text-gray-500 mb-2">攻略迷路を読み込み中...</p>
                                    <p className="text-xs text-gray-400">割り当てられた迷路作成者: {myPlayerState?.assignedMazeOwnerId || "未割り当て"}</p>
                                    {gameData?.mazes && (
                                        <p className="text-xs text-gray-400 mt-2">
                                            利用可能な迷路: {Object.keys(gameData.mazes).join(", ") || "なし"}
                                        </p>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>

                    {/* 右：チャット＆移動宣言 */}
                    <div className="space-y-4">
                        {/* 上部：チャットエリア */}
                        <div className="bg-white rounded-lg shadow-md p-4">
                            <h4 className="text-lg font-semibold mb-3 flex items-center">
                                <MessageSquare size={18} className="mr-2"/> チャット
                            </h4>
                            <div ref={chatLogRef} className="bg-gray-50 p-3 rounded-lg h-40 overflow-y-auto text-sm mb-3 border">
                                {chatMessages.map(msg => (
                                    <div key={msg.id} className={`mb-2 ${msg.senderId === 'system' ? 'text-blue-600 font-semibold' : ''}`}>
                                        <span className="font-medium">{msg.senderName}:</span> {msg.text}
                                    </div>
                                ))}
                            </div>
                            <div className="flex space-x-2">
                                <input 
                                    type="text" 
                                    value={chatInput} 
                                    onChange={(e) => setChatInput(e.target.value)}
                                    onKeyPress={(e) => e.key === 'Enter' && handleSendChatMessage()}
                                    className="flex-1 px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    placeholder="メッセージを入力..."
                                />
                                <button 
                                    onClick={() => handleSendChatMessage()}
                                    className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg transition-colors"
                                    disabled={!chatInput.trim()}
                                >
                                    <Send size={16}/>
                                </button>
                            </div>
                        </div>

                        {/* 下部：移動宣言エリア */}
                        <div className="bg-white rounded-lg shadow-md p-4">
                            <h4 className="text-lg font-semibold mb-3">移動宣言</h4>
                            
                            {isMyStandardTurn && !inStandardBattleBetting ? (
                                <div className="space-y-3">
                                    {/* ターン状態表示 */}
                                    <div className="p-3 bg-green-50 rounded-lg text-center">
                                        <p className="text-green-600 font-semibold">🟢 あなたのターン</p>
                                        <p className="text-sm text-green-500">移動を選択してください</p>
                                    </div>
                                    
                                    {/* 方向ボタン */}
                                    <div className="grid grid-cols-3 gap-2 max-w-48 mx-auto">
                                        <div></div>
                                        <button 
                                            onClick={() => handleStandardMoveImproved('up')}
                                            className="bg-blue-500 hover:bg-blue-600 text-white p-3 rounded-lg flex items-center justify-center transition-colors shadow-md"
                                            title="上に移動 (W キー)"
                                        >
                                            <ArrowUp size={20}/>
                                        </button>
                                        <div></div>
                                        
                                        <button 
                                            onClick={() => handleStandardMoveImproved('left')}
                                            className="bg-blue-500 hover:bg-blue-600 text-white p-3 rounded-lg flex items-center justify-center transition-colors shadow-md"
                                            title="左に移動 (A キー)"
                                        >
                                            <ArrowLeft size={20}/>
                                        </button>
                                        <div className="bg-gray-200 rounded-lg p-3 flex items-center justify-center">
                                            <User size={20} className="text-gray-500"/>
                                        </div>
                                        <button 
                                            onClick={() => handleStandardMoveImproved('right')}
                                            className="bg-blue-500 hover:bg-blue-600 text-white p-3 rounded-lg flex items-center justify-center transition-colors shadow-md"
                                            title="右に移動 (D キー)"
                                        >
                                            <ArrowRight size={20}/>
                                        </button>
                                        
                                        <div></div>
                                        <button 
                                            onClick={() => handleStandardMoveImproved('down')}
                                            className="bg-blue-500 hover:bg-blue-600 text-white p-3 rounded-lg flex items-center justify-center transition-colors shadow-md"
                                            title="下に移動 (S キー)"
                                        >
                                            <ArrowDown size={20}/>
                                        </button>
                                        <div></div>
                                    </div>
                                    
                                    {/* キーボードヒント */}
                                    <div className="text-center text-xs text-gray-500 bg-gray-50 p-2 rounded">
                                        💡 キーボード: ↑↓←→ または WASD でも移動可能
                                    </div>
                                    
                                    {/* プレイヤー情報 */}
                                    <div className="pt-3 border-t">
                                        <h5 className="font-semibold mb-2 text-sm">プレイヤー状況</h5>
                                        <div className="space-y-2">
                                            {gameData?.players?.map(playerId => {
                                                const player = gameData.playerStates[playerId];
                                                const isCurrentPlayer = playerId === userId;
                                                const isActivePlayer = gameData.currentTurnPlayerId === playerId;
                                                
                                                return (
                                                    <div 
                                                        key={playerId}
                                                        className={`p-2 rounded border text-sm ${
                                                            isCurrentPlayer ? 'border-blue-300 bg-blue-50' : 'border-gray-200 bg-gray-50'
                                                        } ${isActivePlayer ? 'ring-2 ring-green-300' : ''}`}
                                                    >
                                                        <div className="flex items-center justify-between">
                                                            <div className="flex items-center space-x-1">
                                                                <User size={14} className={isCurrentPlayer ? 'text-blue-600' : 'text-gray-500'}/>
                                                                <span className={`font-medium ${isCurrentPlayer ? 'text-blue-800' : 'text-gray-700'}`}>
                                                                    {isCurrentPlayer ? 'あなた' : '相手'}
                                                                </span>
                                                                {isActivePlayer && (
                                                                    <span className="bg-green-500 text-white text-xs px-1 py-0.5 rounded">
                                                                        ターン中
                                                                    </span>
                                                                )}
                                                            </div>
                                                            <div className="text-right text-xs">
                                                                <div>スコア: {player?.score || 0}pt</div>
                                                                <div className="text-gray-500">
                                                                    位置: ({player?.position?.r || 0}, {player?.position?.c || 0})
                                                                </div>
                                                            </div>
                                                        </div>
                                                        
                                                        {/* ゴール状態表示 */}
                                                        {player?.goalTime && (
                                                            <div className="mt-1 flex items-center space-x-1">
                                                                <Trophy size={12} className="text-yellow-500"/>
                                                                <span className="text-xs text-yellow-600 font-semibold">ゴール達成！</span>
                                                            </div>
                                                        )}
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                </div>
                            ) : inStandardBattleBetting ? (
                                <div className="text-center p-4 bg-red-50 rounded-lg">
                                    <Swords className="mx-auto mb-2 text-red-600" size={24}/>
                                    <p className="text-red-600 font-semibold">バトル中</p>
                                    <p className="text-sm text-red-500">移動はできません</p>
                                </div>
                            ) : (
                                <div className="text-center p-4 bg-gray-50 rounded-lg">
                                    <Clock className="mx-auto mb-2 text-gray-500" size={24}/>
                                    <p className="text-gray-600 font-semibold">相手のターン</p>
                                    <p className="text-sm text-gray-500">相手の移動を待っています...</p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
        ) : null}

            {/* モーダル */}
            {isBattleModalOpen && (
                <BattleModal
                    isOpen={isBattleModalOpen}
                    onClose={() => setIsBattleModalOpen(false)}
                    gameData={gameData}
                    userId={userId}
                    opponentId={battleOpponentId}
                    onBet={handleStandardBattleBet}
                />
            )}

            {isGameOverModalOpen && (
                <GameOverModal
                    isOpen={isGameOverModalOpen}
                    onClose={() => setIsGameOverModalOpen(false)}
                    gameData={gameData}
                    userId={userId}
                    onReturnToLobby={() => setScreen('lobby')}
                />
            )}
        </div>
    );
}



export default PlayScreen;