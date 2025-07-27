package com.mini.mapper;

import com.mini.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 文件信息Mapper接口
 */
@Mapper
public interface FileInfoMapper {
    
    /**
     * 插入文件信息
     */
    int insert(FileInfo fileInfo);
    
    /**
     * 批量插入文件信息
     */
    int batchInsert(@Param("fileInfos") List<FileInfo> fileInfos);
    
    /**
     * 根据ID查询文件信息
     */
    FileInfo selectById(@Param("id") Long id);
    
    /**
     * 根据文件名查询文件信息
     */
    FileInfo selectByFileName(@Param("fileName") String fileName);
    
    /**
     * 分页查询文件列表
     */
    List<FileInfo> selectPage(@Param("offset") Integer offset, 
                             @Param("pageSize") Integer pageSize,
                             @Param("fileName") String fileName);
    
    /**
     * 查询总记录数
     */
    Long selectCount(@Param("fileName") String fileName);
    
    /**
     * 更新文件信息
     */
    int update(FileInfo fileInfo);
    
    /**
     * 根据ID删除文件信息（逻辑删除）
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 查询7天前的文件
     */
    List<FileInfo> selectExpiredFiles();

    /**
     * 批量查询文件信息
     */
    List<FileInfo> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 批量逻辑删除
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);
}