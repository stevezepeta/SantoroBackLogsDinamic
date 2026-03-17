package backlogs.dinamico.service.ai.dto;

public class SystemCatalogItemDto {

    public String name;

    public long count;

    public SystemCatalogItemDto() {
    }

    public SystemCatalogItemDto(String name, long count) {
        this.name = name;
        this.count = count;
    }

}
