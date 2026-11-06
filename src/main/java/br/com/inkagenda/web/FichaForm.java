package br.com.inkagenda.web;

import br.com.inkagenda.service.DadosAnamnese;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FichaForm {

    @Size(max = 255, message = "Resposta muito longa")
    private String alergias;

    @Size(max = 255, message = "Resposta muito longa")
    private String medicamentos;

    @Size(max = 255, message = "Resposta muito longa")
    private String problemasCicatrizacao;

    private boolean diabetes;

    private boolean gestacao;

    @Size(max = 2000, message = "As observações devem ter até 2000 caracteres")
    private String observacoes;

    private boolean consentimentoDadosSaude;

    DadosAnamnese paraDados() {
        return new DadosAnamnese(alergias, medicamentos, problemasCicatrizacao, diabetes, gestacao,
                observacoes, consentimentoDadosSaude);
    }
}
