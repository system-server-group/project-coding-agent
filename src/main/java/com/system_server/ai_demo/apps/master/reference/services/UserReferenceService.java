package com.system_server.ai_demo.apps.master.reference.services;

import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * ユーザ参照サービス。ユーザマスタを参照し、ユーザ名一覧（ユーザID昇順）取得、ユーザIDに対応するユーザ名・部署IDの取得を行う。
 */
@Service
public class UserReferenceService {

    private final UsersMapper usersMapper;

    public UserReferenceService(UsersMapper usersMapper) {
        this.usersMapper = usersMapper;
    }

    /**
     * 全てのユーザ名をユーザID昇順で取得する。
     *
     * @return ユーザ名の一覧（ユーザID昇順）
     */
    public List<String> getUserNames() {
        return usersMapper.findAll().stream().map(u -> u.getUserName()).toList();
    }

    /**
     * ユーザIDに対応するユーザ名を取得する。
     *
     * @param userId ユーザID
     * @return ユーザ名、該当ユーザが存在しない場合は {@code null}
     */
    public String getUserName(String userId) {
        UsersEntity user = usersMapper.findByUserId(userId);
        return user == null ? null : user.getUserName();
    }

    /**
     * ユーザIDに対応するユーザの部署IDを取得する。
     *
     * @param userId ユーザID
     * @return 部署ID、該当ユーザが存在しない場合は {@code null}
     */
    public Integer getUserDepartmentId(String userId) {
        UsersEntity user = usersMapper.findByUserId(userId);
        return user == null ? null : user.getDepartmentId();
    }
}
