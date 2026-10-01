package com.cafeoccidente.backend.supplies.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Caja.Rel_cheques = Yes de Access: el cheque (source, sourceId) ya salio en "Relacion Cheques". */
@Entity
@Table(name = "check_relation_mark")
@IdClass(CheckRelationMark.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckRelationMark {

    @Id
    @Column(nullable = false)
    private String source;

    @Id
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private String source;
        private Long sourceId;
    }
}
