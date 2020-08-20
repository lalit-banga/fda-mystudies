/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.repository;

import com.google.cloud.healthcare.fdamystudies.usermgmt.model.UserDetailsBO;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public interface UserDetailsBORepository extends JpaRepository<UserDetailsBO, Integer> {

  interface MyView {
    String getEmailCode();
  }

  <T> List<T> findByUserId(String userId, Class<T> d);

  UserDetailsBO findByUserId(String userId);

  List<UserDetailsBO> findByEmail(String emailId);

  Optional<UserDetailsBO> findByEmailAndAppInfoId(String email, Integer appInfoId);

  Optional<UserDetailsBO> findByUserDetailsId(Integer userDetailsId);
}
