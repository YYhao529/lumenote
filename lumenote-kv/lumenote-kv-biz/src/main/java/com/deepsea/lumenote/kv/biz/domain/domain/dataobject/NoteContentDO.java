package com.deepsea.lumenote.kv.biz.domain.domain.dataobject;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.UUID;

@Table("note_content")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteContentDO {

    @PrimaryKey("id")
    private UUID id;

    private String content;
}
