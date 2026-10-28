package com.xiaoying.studio.asset;

/** INSERT 参数对象：MyBatis 需要可写的 id 字段回填自增主键。 */
public class NewAsset {
    public Long id;
    public long userId;
    public String templateId;
    public String originalName;
    public String mimeType;
    public long sizeBytes;
    public String storagePath;
    public String url;
}
