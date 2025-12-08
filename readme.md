# 快速开始

[SDK Sample]

下载下来的Sample 代码，需要进行一定的配置。主要是替换SN 授权文件，和用户凭证为自己的。

- 将dataBeans/CONSTANT 类中的`const val CLIENT_SECRET = ""` 的值替换为个人凭证。
- 将授权文件保存到：res/raw/ 目录下。
- 将dataBeans/CONSTANT 类中的`fun getSNResource() = R.raw.`方法的返回补全为授权文件。

![image-20251208170356634](/Users/shuigao/Library/Application Support/typora-user-images/image-20251208170356634.png)