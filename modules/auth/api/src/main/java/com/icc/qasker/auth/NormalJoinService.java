package com.icc.qasker.auth;

import com.icc.qasker.auth.dto.request.JoinRequest;

public interface NormalJoinService {

    void register(JoinRequest normalJoinRequest);
}

