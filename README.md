## Docker

`docker pull hoywu/gradereminder`

| Environment Variable | Description                                                                 | Example                                                                              |
|----------------------|-----------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `requestURL`         | Educational administration system URL                                       | `https://*****.*****.edu.cn/cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N******&su=` |
| `studentID`          | Student Number, split by comma                                              | `0000000001,0000000002`                                                              |
| `cookie`             | Cookie, split by comma, same order as `studentID`                           | `route=***; JSESSIONID=***,route=***; JSESSIONID=***`                                |
| `checkDelay`         | Delay between each check, in milliseconds                                   | `10000`                                                                              |
| `userAgent`          | User-Agent, if needed                                                       | `Mozilla/5.0`                                                                        | 
| `tgBotUrl`           | Telegram Notification push URL                                              | `https://api.telegram.org/bot<token>/sendMessage?chat_id=***`                        |  
| `PushTargetByUserID` | WeChat Push target user id, split by comma, same order as `studentID`       | `user1,user2`                                                                        |
| `agentID`            | WeChatWork Application agentID, necessary if `PushTargetByUserID` is set    | `1000001`                                                                            |
| `corpId`             | WeChatWork corpID, necessary if `PushTargetByUserID` is set                 | `wwb12345678901234e`                                                                 |
| `corpSecret`         | WeChatWork Application corpSecret, necessary if `PushTargetByUserID` is set |                                                                                      |
| `debug`              | Print Debug Information                                                     | `0`                                                                                  |

