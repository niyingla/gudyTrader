package com.gudy.counter.service;

import com.gudy.counter.bean.res.Account;
import com.gudy.counter.cache.CacheType;
import com.gudy.counter.cache.RedisStringCache;
import com.gudy.counter.util.DbUtil;
import com.gudy.counter.util.JsonUtil;
import com.gudy.counter.util.TimeformatUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import thirdpart.uuid.GudyUuid;

import java.util.Date;

@Component
public class AccountServiceImpl implements AccountService {
    @Override
    public Account login(long uid, String password, String captcha, String captchaId) throws Exception {
        //1.入参的合法性校验
        if(StringUtils.isAnyBlank(password,captcha, captchaId)){
            return null;
        }
        //2.校验缓存验证码
        String captchaCache = RedisStringCache.get(captchaId, CacheType.CAPTCHA);
        if(StringUtils.isEmpty(captchaCache)){
            return null;
        }else if(!StringUtils.equalsIgnoreCase(captcha,captchaCache)){
            return null;
        }
        RedisStringCache.remove(captchaId,CacheType.CAPTCHA);
        //3.比对数据库用户名和密码
        Account account = DbUtil.queryAccount(uid, password);
        if(account == null){
            return null;
        }else {
            //增加唯一ID作为身份标志
            account.setToken(String.valueOf(GudyUuid.getInstance().getUUID()));

            //存入缓存
            RedisStringCache.cache(String.valueOf(account.getToken()), JsonUtil.toJson(account), CacheType.ACCOUNT);

            //更新登录时间
            Date date = new Date();
            DbUtil.updateLoginTime(uid, TimeformatUtil.yyyyMMdd(date), TimeformatUtil.hhMMss(date));

            return account;
        }
    }

    /**
     * 判断token是否合法
     * @param token
     * @return
     */
    @Override
    public boolean accountExistInCache(String token) {
        if(StringUtils.isBlank(token)){
            return false;
        }

        //从缓存获取数据
        String acc = RedisStringCache.get(token, CacheType.ACCOUNT);
        if(acc != null){
            RedisStringCache.cache(token,acc,CacheType.ACCOUNT);
            return true;
        }else {
            return false;
        }
    }

    //清除缓存登录信息
    @Override
    public boolean logout(String token) {
        RedisStringCache.remove(token,CacheType.ACCOUNT);
        return true;
    }

    @Override
    public boolean updatePwd(long uid, String oldPwd, String newPwd) {
        int res = DbUtil.updatePwd(uid,oldPwd,newPwd);
        return res == 0 ? false : true;
    }
}
