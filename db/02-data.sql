-- 初始化数据：模板 + 两个演示用户。封面文件位于 backend/src/main/resources/static/demo/
SET NAMES utf8mb4;
USE studio;

INSERT INTO template (id, title, category, cover_url, description, required_image_count, accepted_mime_types, max_file_size_bytes, sort_order) VALUES
('demo-postcard',  '旅行明信片', '创意图片', '/demo/postcard-cover.png', '上传两张旅行照片，模拟生成一张拼贴明信片。', 2, 'image/jpeg,image/png', 5242880, 10),
('demo-polaroid',  '拍立得相框', '创意图片', '/demo/polaroid-cover.png', '上传一张照片，模拟生成复古拍立得效果。', 1, 'image/jpeg,image/png', 5242880, 20),
('demo-triptych',  '三联画',     '创意图片', '/demo/triptych-cover.png', '上传三张图片，横向排成三联画。', 3, 'image/jpeg,image/png', 5242880, 30),
('pet-portrait',   '萌宠肖像',   '宠物',     '/demo/pet-cover.png',      '上传一张宠物照片，模拟生成艺术肖像。仅接受 PNG，单张不超过 2MB。', 1, 'image/png', 2097152, 10),
('pet-duo',        '毛孩子合影', '宠物',     '/demo/petduo-cover.png',   '上传两张宠物照片，生成双格合影。', 2, 'image/jpeg,image/png', 5242880, 20),
('xmas-card',      '圣诞贺卡',   '节日',     '/demo/xmas-cover.png',     '上传一张人像，生成节日贺卡。仅接受 JPEG。', 1, 'image/jpeg', 5242880, 10),
('newyear-banner', '新年横幅',   '节日',     '/demo/newyear-cover.png',  '上传两张照片，生成新年横幅。', 2, 'image/jpeg,image/png', 5242880, 20),
('wedding-album',  '婚礼相册',   '婚礼写真', '/demo/wedding-cover.png',  '上传三张婚纱照，生成相册封面。', 3, 'image/jpeg,image/png', 8388608, 10)
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO app_user (nickname) VALUES ('alice'), ('bob')
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);
