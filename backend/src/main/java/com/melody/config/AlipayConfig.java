package com.melody.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 支付宝沙箱配置（PC 网页支付）
 *
 * 凭证写在 application.yml 的 alipay 块：
 *  - privateKey = 应用私钥（商户生成，调支付宝下单时签名用）
 *  - publicKey = 支付宝公钥（验签支付宝回调用）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {

    private String appId;
    private String gateway;
    private String privateKey;
    private String publicKey;
    private String charset = "UTF-8";
    private String signType = "RSA2";
    private String format = "json";

    /** 付款后浏览器回跳的前台页面 */
    private String returnUrl;
    /** 异步通知地址（需公网可达；本地沙箱打不到，靠主动查询兜底） */
    private String notifyUrl;

    /** 订单支付回跳页面（{id} 运行时替换为订单 id） */
    private String orderReturnUrl;
    /** 订单支付异步通知地址（独立于充值） */
    private String orderNotifyUrl;

    /**
     * AlipayClient 单例。7 参构造：网关 / appId / 应用私钥 / format / charset / 支付宝公钥 / 签名类型。
     */
    @Bean
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(gateway, appId, privateKey, format, charset, publicKey, signType);
    }

    /** 主动事务模板：markPaid（标单）+ rechargeByGateway（加余额）需在同一事务，幂等才完整。 */
    @Bean
    public TransactionTemplate alipayTransactionTemplate(PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }
}
