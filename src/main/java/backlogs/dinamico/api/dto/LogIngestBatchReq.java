package backlogs.dinamico.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogIngestBatchReq {

    @NotNull
    @Size(min = 1, max = 1000, message = "batch_size_1_1000")
    private List<@Valid LogIngestReq> items;

}
