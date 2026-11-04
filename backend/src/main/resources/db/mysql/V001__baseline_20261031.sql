-- Current application schema for a new empty MySQL 8.0+ database.
-- Legacy populated databases require separate upgrade review.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admins` (
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `admin_id` varchar(36) NOT NULL,
  `token_version` varchar(36) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  PRIMARY KEY (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_model_metrics` (
  `metric_value` double NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `change_value` varchar(20) DEFAULT NULL,
  `unit` varchar(20) DEFAULT NULL,
  `metric_key` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_pipelines` (
  `resource_usage_rate` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `avg_latency` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL,
  `engine_name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_retrain_histories` (
  `created_at` datetime(6) NOT NULL,
  `executed_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `history_id` varchar(36) NOT NULL,
  `performance_change` varchar(50) NOT NULL,
  `trigger_reason` varchar(100) NOT NULL,
  PRIMARY KEY (`history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `analysis_answers` (
  `turn` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `client_request_id` varchar(36) NOT NULL,
  `id` varchar(36) NOT NULL,
  `session_id` varchar(36) NOT NULL,
  `answer` varchar(100) DEFAULT NULL,
  `question_id` varchar(100) NOT NULL,
  `answer_label` varchar(200) DEFAULT NULL,
  `question_text` varchar(2000) DEFAULT NULL,
  `user_text` varchar(2000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmwly1hkjc87ov4i7gp30j9xty` (`session_id`,`client_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `analysis_sessions` (
  `pain_score` int DEFAULT NULL,
  `turn` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `image_type` varchar(30) NOT NULL,
  `status` varchar(30) NOT NULL,
  `case_id` varchar(36) NOT NULL,
  `client_request_id` varchar(36) NOT NULL,
  `id` varchar(36) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `error_code` varchar(50) DEFAULT NULL,
  `progress_note` varchar(2000) DEFAULT NULL,
  `image` longblob NOT NULL,
  `question_json` longtext,
  `result_json` longtext,
  `slots_json` longtext NOT NULL,
  `vision_json` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK90x5kwfix59q28kcmvjbkdo8r` (`user_id`,`client_request_id`),
  KEY `ix_analysis_owner_created` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `diagnoses` (
  `confidence_score` double NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `diagnosis_id` varchar(36) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `suspected_disease` varchar(100) NOT NULL,
  `image_url` varchar(500) NOT NULL,
  `admin_note` text,
  `first_aid_guide` text,
  `precautions_guide` text,
  `symptoms` text,
  `body_part` enum('ARM','FACE','HAND_FOOT','LEG','OTHER','TORSO') NOT NULL,
  `risk_level` enum('EMERGENCY','NORMAL','OBSERVATION','WARNING') NOT NULL,
  `status` enum('ALERT_SENT','COMPLETED','IN_PROGRESS','PENDING') NOT NULL,
  PRIMARY KEY (`diagnosis_id`),
  KEY `FK8yb1akh7c33dcle4usb0q8eo8` (`user_id`),
  CONSTRAINT `FK8yb1akh7c33dcle4usb0q8eo8` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `diagnosis_metrics` (
  `segmentation_iou` double DEFAULT NULL,
  `inference_time` varchar(20) DEFAULT NULL,
  `diagnosis_id` varchar(36) NOT NULL,
  `affected_area_size` varchar(50) DEFAULT NULL,
  `bounding_box` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`diagnosis_id`),
  CONSTRAINT `FKsgrkkru9f30a0dfqx6p0p27dx` FOREIGN KEY (`diagnosis_id`) REFERENCES `diagnoses` (`diagnosis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_verification_tokens` (
  `is_verified` bit(1) NOT NULL,
  `auth_code` varchar(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expiration_time` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `email` varchar(100) NOT NULL,
  PRIMARY KEY (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hospitals` (
  `is_emergency` bit(1) NOT NULL,
  `is_night_care` bit(1) NOT NULL,
  `verified_on` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `status` varchar(20) NOT NULL,
  `tel` varchar(20) DEFAULT NULL,
  `data_source` varchar(50) DEFAULT NULL,
  `hospital_id` varchar(50) NOT NULL,
  `region` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL,
  `address` varchar(200) NOT NULL,
  `source_url` varchar(1000) DEFAULT NULL,
  `hospital_type` enum('DERMATOLOGY','GENERAL_HOSPITAL','OTHER','PHARMACY') NOT NULL,
  PRIMARY KEY (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inquiries` (
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `analysis_id` varchar(36) DEFAULT NULL,
  `client_request_id` varchar(36) DEFAULT NULL,
  `diagnosis_id` varchar(36) DEFAULT NULL,
  `inquiry_id` varchar(36) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` text NOT NULL,
  `inquiry_type` enum('ACCURACY','AI_RESULT','OTHER','SYSTEM_ERROR') NOT NULL,
  `status` enum('COMPLETED','WAITING') NOT NULL,
  PRIMARY KEY (`inquiry_id`),
  UNIQUE KEY `uk_inquiry_user_request` (`user_id`,`client_request_id`),
  KEY `FKdd4pdui1tsr4ad5npoxx4ubng` (`diagnosis_id`),
  CONSTRAINT `FKdd4pdui1tsr4ad5npoxx4ubng` FOREIGN KEY (`diagnosis_id`) REFERENCES `diagnoses` (`diagnosis_id`),
  CONSTRAINT `FKfks94q8sobcuibrudbr3im380` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inquiry_answers` (
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `admin_id` varchar(36) DEFAULT NULL,
  `answer_id` varchar(36) NOT NULL,
  `inquiry_id` varchar(36) NOT NULL,
  `answer_content` text NOT NULL,
  PRIMARY KEY (`answer_id`),
  UNIQUE KEY `UKqlaipqsmq1rkanl6pho6og5k1` (`inquiry_id`),
  CONSTRAINT `FKl2i9wnoa61tfawfdlpoheeek2` FOREIGN KEY (`inquiry_id`) REFERENCES `inquiries` (`inquiry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_reset_tokens` (
  `is_used` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expiration_time` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `token_id` varchar(36) NOT NULL,
  `email` varchar(100) NOT NULL,
  PRIMARY KEY (`token_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `recoveries` (
  `pain_score` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `diagnosis_id` varchar(36) NOT NULL,
  `recovery_id` varchar(36) NOT NULL,
  `image_url` varchar(500) NOT NULL,
  `memo` text,
  PRIMARY KEY (`recovery_id`),
  KEY `FKakvllr6nqeqc9chsqlwc6th03` (`diagnosis_id`),
  CONSTRAINT `FKakvllr6nqeqc9chsqlwc6th03` FOREIGN KEY (`diagnosis_id`) REFERENCES `diagnoses` (`diagnosis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_refresh_tokens` (
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` varchar(36) NOT NULL,
  `token_value` varchar(500) NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_settings` (
  `push_diagnosis_alert` bit(1) NOT NULL,
  `push_recovery_reminder` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` varchar(36) NOT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `FK8v82nj88rmai0nyck19f873dw` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `agreed_privacy` bit(1) NOT NULL,
  `agreed_terms` bit(1) NOT NULL,
  `consented_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `last_access_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `token_version` varchar(36) DEFAULT NULL,
  `user_id` varchar(36) NOT NULL,
  `privacy_version` varchar(40) DEFAULT NULL,
  `terms_version` varchar(40) DEFAULT NULL,
  `nickname` varchar(50) NOT NULL,
  `email` varchar(100) NOT NULL,
  `profile_image_url` varchar(500) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `plan` enum('FREE','PREMIUM') NOT NULL,
  `provider` enum('GOOGLE','LOCAL') NOT NULL,
  `status` enum('ACTIVE','DELETED','INACTIVE') NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

