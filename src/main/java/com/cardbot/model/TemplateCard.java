package com.cardbot.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "template_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_set_id", nullable = false)
    private TemplateSet templateSet;

    @Column(name = "word", nullable = false, length = 500)
    private String word;

    @Column(name = "translation", nullable = false, length = 500)
    private String translation;

    @Column(name = "transcription", length = 500)
    private String transcription;
}
