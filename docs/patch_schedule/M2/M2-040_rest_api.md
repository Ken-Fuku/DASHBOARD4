# M2-040: REST Push/Pull API（簡易認証）

説明:
- /sync/push (POST: upload changelog file or JSON lines) /sync/pull (GET: changelog since lsn) を提供する軽量サーバ実装。Token ベース認証（初期は静的トークン）。

受入条件:
- ローカルで API を起動し、CLI の HTTP クライアントから push/pull が成功すること
- トークンなしのアクセスは拒否されること
- 単体・統合テストあり

ラベル: backend, api, security, test
想定工数: 1.5 days
