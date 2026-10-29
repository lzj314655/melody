package com.melody.service;

/**
 * 图片验证码服务接口
 */
public interface ICaptchaService {

    /** 生成 4 位随机码(测试用,前端不需要) */
    String generateCode();

    /** 创建一条验证码,返回 id(后续提交时带上) */
    String create();

    /** 根据 id 取回 code(仅供生成 SVG 时使用) */
    String getCode(String id);

    /** 校验 + 一次性消费 */
    boolean verify(String id, String code);
}