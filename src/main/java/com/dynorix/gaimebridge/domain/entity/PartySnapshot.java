package com.dynorix.gaimebridge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class PartySnapshot {

    @Column(name = "name")
    private String name;

    @Column(name = "tax_id")
    private String taxId;
}
