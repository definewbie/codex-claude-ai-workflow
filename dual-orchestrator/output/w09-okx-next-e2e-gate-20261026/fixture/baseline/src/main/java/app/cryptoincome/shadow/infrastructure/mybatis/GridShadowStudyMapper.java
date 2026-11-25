package app.cryptoincome.shadow.infrastructure.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

public interface GridShadowStudyMapper extends BaseMapper<GridShadowStudyEntity> {
    @Select("SELECT * FROM grid_shadow_study WHERE id=#{id} FOR UPDATE")
    GridShadowStudyEntity findByIdForUpdate(long id);

    @Update("UPDATE grid_shadow_study SET state='STOPPED', stopped_at=#{stoppedAt} WHERE id=#{id} AND state='ACTIVE'")
    int stopActive(long id, Instant stoppedAt);
}
