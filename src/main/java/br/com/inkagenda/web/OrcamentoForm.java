package br.com.inkagenda.web;

import br.com.inkagenda.domain.enums.EstiloTatuagem;
import br.com.inkagenda.service.NovaSolicitacao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class OrcamentoForm {

    @NotBlank(message = "Informe seu nome")
    @Size(max = 255, message = "Nome muito longo")
    private String nome;

    @NotBlank(message = "Informe seu WhatsApp")
    private String whatsapp;

    @NotNull(message = "Informe o tamanho em cm")
    @Min(value = 1, message = "O tamanho deve ser de ao menos 1 cm")
    @Max(value = 300, message = "O tamanho deve ser de até 300 cm")
    private Integer tamanhoCm;

    @NotBlank(message = "Informe a região do corpo")
    @Size(max = 255, message = "Região muito longa")
    private String regiaoCorpo;

    @NotNull(message = "Escolha um estilo")
    private EstiloTatuagem estilo;

    private boolean cobertura;

    @Size(max = 2000, message = "A descrição deve ter até 2000 caracteres")
    private String descricao;

    @Size(max = 255, message = "A preferência deve ter até 255 caracteres")
    private String preferenciaDias;

    private boolean aceitePrivacidade;

    private List<MultipartFile> imagens;

    private MultipartFile fotoAtual;

    NovaSolicitacao paraSolicitacao() {
        return new NovaSolicitacao(nome, whatsapp, tamanhoCm, regiaoCorpo, estilo, cobertura,
                descricao, preferenciaDias, aceitePrivacidade, ArquivosWeb.arquivos(imagens),
                ArquivosWeb.arquivo(fotoAtual));
    }
}
