package com.metamong.server.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Room extends BaseEntity {

    private String name;

    private int maxPopulation;

    /* �渶�� ������ ���� ����Ʈ */
    @OneToMany(mappedBy = "room")
    private List<User> users = new ArrayList<>();
}
