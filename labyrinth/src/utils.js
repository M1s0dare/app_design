/**
 * 便利な関数をまとめる
 * １．配列のシャッフル関数
 * ２．初めの壁とマスを作成する関数
 * ３．経路探索の可能性判定関数
 */

// 配列をランダムにシャッフルする関数(Fisher-Yatesアルゴリズムを使用)
export function shuffleArray(array) {
    let currentIndex = array.length, randomIndex; // letは，数値が変更でき，{}の中のみ有効．randomIndexは，0以上currentIndex未満の値でランダムな整数

    // 配列の末尾から始めて、要素をランダムに入れ替える
    while (currentIndex !== 0) {
        // 残りの要素からランダムなインデックスを選ぶ
        randomIndex = Math.floor(Math.random() * currentIndex); // Math.random()は0以上1未満のランダムな小数を生成し，Math.floor()は小数点以下を切り捨てて整数にする
        currentIndex--; // 現在のインデックスを1つ減らす

        // 選んだ要素と現在の要素を入れ替える
        [array[currentIndex], array[randomIndex]] = [array[randomIndex], array[currentIndex]]; // 配列の要素を入れ替える
    }
    return array; // シャッフルされた配列を返す
}