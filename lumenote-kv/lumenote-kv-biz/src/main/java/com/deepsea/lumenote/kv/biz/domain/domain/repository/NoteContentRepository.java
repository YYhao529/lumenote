package com.deepsea.lumenote.kv.biz.domain.domain.repository;

import com.deepsea.lumenote.kv.biz.domain.domain.dataobject.NoteContentDO;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.UUID;

public interface NoteContentRepository extends CassandraRepository<NoteContentDO, UUID> {

}
