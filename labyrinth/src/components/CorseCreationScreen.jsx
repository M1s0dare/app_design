/**
 * コース作成画面のコンポーネント
 */

import React, { useState, useEffect} from 'react';
import { getFirestore, doc, getDoc, updateDoc, serverTimestamp, Timestamp, onSnapshot } from 'firebase/firestore'; // Firebaseから必要な関数をインポート
import { db, appId } from '../firebase'; // Firebaseの設定をインポート
import { CheckCircle, XCircle } from 'lucide-react'; // 軽量SVGアイコンライブラリからアイコンをインポート（チェックマーク・バツマーク）
import MaizeGrid from './MaizeGrid'; // グリッドコンポーネントをインポート
import { GridSize, Wall_count } from '../constants'; // 定数をインポート(グリッドサイズと壁の数)
