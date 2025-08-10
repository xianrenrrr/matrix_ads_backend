package com.example.demo.dao;

import com.example.demo.model.Invite;
import java.util.List;

public interface InviteDao {
    void save(Invite invite);
    void update(Invite invite);
    Invite findByToken(String token);
    Invite findById(String id);
    List<Invite> findByManagerId(String managerId);
    List<Invite> findByStatus(String status);
    void delete(String id);
    void updateStatus(String id, String status);
    List<Invite> findExpiredInvites();
}
