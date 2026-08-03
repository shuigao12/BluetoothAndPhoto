眼镜麦克风离线语音识别（Vosk）模型说明
========================================

1. 下载中文小模型（约 42MB）：
   https://alphacephei.com/vosk/models
   推荐：vosk-model-small-cn-0.22

2. 解压后，将整个文件夹复制到本目录，最终路径为：
   app/src/main/assets/vosk-model-small-cn-0.22/
   （无需 uuid 文件；App 会自动复制到本地存储）
   （文件夹内应包含 am/final.mdl、graph/ 等文件）

3. 重新编译安装 App。PictureActivity 前台时会优先使用「眼镜麦克风 + Vosk」识别「识别图片」。

4. 若未放置模型，将自动降级为「手机麦克风 + 系统 SpeechRecognizer」（需安装 Google 语音服务等）。
