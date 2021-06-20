--
-- 由SQLiteStudio v3.1.1 产生的文件 周三 3月 10 09:11:30 2021
--
-- 文本编码：UTF-8
--
PRAGMA foreign_keys = off;
BEGIN TRANSACTION;

CREATE TABLE `t_user`  ( `id` int(0) NOT NULL, `uid` int(0) NULL,`password` varchar(255) NULL,PRIMARY KEY (`id`));

-- 表：t_order
CREATE TABLE t_order (id INTEGER PRIMARY KEY UNIQUE NOT NULL, uid BIGINT NOT NULL, code INTEGER NOT NULL, direction INTEGER NOT NULL, type INTEGER NOT NULL, price BIGINT NOT NULL, ocount BIGINT NOT NULL, status INTEGER, date VARCHAR (8), time VARCHAR (8));

-- 表：t_posi
CREATE TABLE t_posi (id INTEGER PRIMARY KEY UNIQUE NOT NULL, uid BIGINT NOT NULL, code INTEGER (10) NOT NULL, cost BIGINT NOT NULL DEFAULT NULL, count BIGINT NOT NULL);

-- 表：t_stock
CREATE TABLE t_stock (code INTEGER PRIMARY KEY, name VARCHAR (20) NOT NULL, abbrName VARCHAR (10) NOT NULL, status INT);
INSERT INTO t_stock (code, name, abbrName, status) VALUES (1, '平安银行', 'payh', 0);
INSERT INTO t_stock (code, name, abbrName, status) VALUES (500086, '江苏银行', 'jsyh', 0);
INSERT INTO t_stock (code, name, abbrName, status) VALUES (510020, '中国银行', 'zgyh', 0);
INSERT INTO t_stock (code, name, abbrName, status) VALUES (561452, '工商银行', 'gsyh', 0);
INSERT INTO t_stock (code, name, abbrName, status) VALUES (600000, '浦发银行', 'pfyh', 0);
INSERT INTO t_stock (code, name, abbrName, status) VALUES (600012, '上海银行', 'shyh', 0);
INSERT INTO t_stock (code, name, abbrName, status) VALUES (623001, '建设银行', 'jsyh', 0);

-- 表：t_trade
CREATE TABLE t_trade (id BIGINT NOT NULL, uid BIGINT NOT NULL, code INTEGER NOT NULL, direction INTEGER NOT NULL, price BIGINT NOT NULL, tcount BIGINT NOT NULL, oid INTEGER NOT NULL, date VARCHAR (8) NOT NULL, time VARCHAR (8) NOT NULL);

-- 表：t_user
CREATE TABLE t_user (id INTEGER PRIMARY KEY NOT NULL DEFAULT NULL, uid BIGINT UNIQUE NOT NULL DEFAULT NULL, password VARCHAR (64) DEFAULT NULL, balance BIGINT CONSTRAINT 空 DEFAULT NULL, createDate CHAR (8) DEFAULT NULL, createTime CHAR (8) DEFAULT (NULL), modifyDate CHAR (8) DEFAULT NULL, modifyTime CHAR (8) DEFAULT NULL);
INSERT INTO t_user (id, uid, password, balance, createDate, createTime, modifyDate, modifyTime) VALUES (1, 123456, 'e10adc3949ba59abbe56e057f20f883e', 200000000, '20201203', '11:13:05', '20201209', '15:42:47');

COMMIT TRANSACTION;
PRAGMA foreign_keys = on;