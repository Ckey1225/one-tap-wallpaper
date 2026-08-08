# One-Tap Wallpaper 签名密钥备份（v1.1.5 起启用）

> 该文件按开发者要求随仓库公开，仅作凭据备份。任何可 clone 本仓库的人均可获得此签名能力，请自行评估风险。

## 密钥库信息

| 项目 | 值 |
| --- | --- |
| 密钥库文件 | `one-tap-wallpaper-release.jks`（仓库根目录） |
| 证书 CN | `Ckey1225` |
| 算法 / 位数 | RSA 2048 |
| 有效期 | 100 年（36500 天） |
| 密钥库密码（storepass） | `c159243768` |
| 密钥别名（alias） | `one-tap-wallpaper` |
| 密钥密码（keypass） | `c159243768` |

## 签名信息（apksigner 输出）

```
Signer #1 certificate DN: CN=Ckey1225
SHA-256 digest: a0cdb9bd9cece16fea57b91f3ba1f7863da3aaf211d7bca2136194f96aa4470e
```

## 使用

```bash
# 用 keytool 查看指纹
keytool -list -v -keystore one-tap-wallpaper-release.jks -storepass c159243768
```