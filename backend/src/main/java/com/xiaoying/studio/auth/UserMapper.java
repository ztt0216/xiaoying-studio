package com.xiaoying.studio.auth;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("SELECT id, nickname, created_at FROM app_user WHERE nickname = #{nickname}")
    AuthUser findByNickname(String nickname);

    /** 昵称唯一；并发登录同一昵称时 INSERT IGNORE 保证不报错。 */
    @Insert("INSERT IGNORE INTO app_user (nickname) VALUES (#{nickname})")
    int insertIgnore(String nickname);

    @Insert("INSERT INTO user_token (token, user_id) VALUES (#{token}, #{userId})")
    int insertToken(@Param("token") String token, @Param("userId") long userId);

    @Select("""
            SELECT u.id, u.nickname, u.created_at
            FROM user_token t JOIN app_user u ON u.id = t.user_id
            WHERE t.token = #{token}
            """)
    AuthUser findByToken(String token);

    @Delete("DELETE FROM user_token WHERE token = #{token}")
    int deleteToken(String token);
}
