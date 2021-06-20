package com.gudy.counter.service;

import com.gudy.counter.bean.res.Account;

public interface AccountService {

    /**
     * 登陆
     * @param uid
     * @param password
     * @param captcha
     * @param captchaId
     * @return
     * @throws Exception
     */
    Account login(long uid,String password,
                  String captcha,String captchaId) throws Exception;

    /**
     * 缓存中是否存在已登录信息
     * @param token
     * @return
     */
    boolean accountExistInCache(String token);


    /**
     * 退出登录
     * @param token
     * @return
     */
    boolean logout(String token);


    boolean updatePwd(long uid,String oldPwd,String newPwd);


}
