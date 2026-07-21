package com.cavale.gym.domain;

import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.cavale.common.domain.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One face of a template — "Force Max A" — holding the actual exercises. */
@Entity
@Table(name = "gym_template_variant")
public class GymTemplateVariant extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** EAGER: "Force Max · A" must be printable wherever a variant travels. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private GymTemplate template;

    @Column(nullable = false, length = 10)
    private String label;

    @Column(length = 300)
    private String note;

    /** Loops of a circuit variant — null means a classic sets×reps program. */
    @Column(name = "circuit_loops")
    private Integer circuitLoops;

    /** Rest between circuit loops, seconds. */
    @Column(name = "circuit_rest_sec")
    private Integer circuitRestSec;

    protected GymTemplateVariant() {
    }

    public GymTemplateVariant(GymTemplate template, String label, String note) {
        this.template = template;
        this.label = label;
        this.note = note;
    }

    public void update(String label, String note) {
        this.label = label;
        this.note = note;
    }

    /** Turn the variant into a circuit (loops ≥ 1) or back into a classic program (null). */
    public void configureCircuit(Integer loops, Integer restSec) {
        this.circuitLoops = loops;
        this.circuitRestSec = loops != null ? restSec : null;
    }

    public boolean isCircuit() {
        return circuitLoops != null;
    }

    public UUID getId() {
        return id;
    }

    public GymTemplate getTemplate() {
        return template;
    }

    public String getLabel() {
        return label;
    }

    public String getNote() {
        return note;
    }

    public Integer getCircuitLoops() {
        return circuitLoops;
    }

    public Integer getCircuitRestSec() {
        return circuitRestSec;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        GymTemplateVariant other = (GymTemplateVariant) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
